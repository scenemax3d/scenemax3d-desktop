"""Conservative, transactional route edits inside an existing Nginx TLS vhost.

No certificates, listeners, other locations or service lifecycle are changed here.
The installer holds a global Nginx edit lock and owns validation/reload/rollback.
"""
import glob
import json
import os
from pathlib import Path
import re
import shlex
import subprocess
import sys
import tempfile
from urllib.parse import urlsplit


TOKEN = re.compile(r'''\s+|\#[^\n]*|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|[{};]|(?:\$\{[^}]*\}|\\.|[^\s{};\#"'\\])+''')


def parse(text):
    tokens = []
    end = 0
    for m in TOKEN.finditer(text):
        if m.start() != end:
            raise ValueError('Unsupported Nginx syntax; no configuration changed')
        end = m.end()
        if not m[0].isspace() and not m[0].startswith('#'):
            tokens.append((m[0], m.start(), m.end()))
    if end != len(text):
        raise ValueError('Unsupported Nginx syntax')
    index = 0

    def block(nested=False):
        nonlocal index
        result = []
        while index < len(tokens):
            if tokens[index][0] == '}':
                if not nested:
                    raise ValueError('Unexpected closing brace')
                return result
            start = tokens[index][1]
            words = []
            while index < len(tokens) and tokens[index][0] not in ('{', '}', ';'):
                word = tokens[index][0]
                words.append(shlex.split(word)[0] if word[:1] in ('"', "'") else word)
                index += 1
            if not words or index == len(tokens) or tokens[index][0] == '}':
                raise ValueError('Invalid Nginx directive')
            delimiter = tokens[index][0]
            index += 1
            children = block(True) if delimiter == '{' else None
            if children is not None:
                if index == len(tokens):
                    raise ValueError('Unclosed Nginx block')
                close = tokens[index][1]
                finish = tokens[index][2]
                index += 1
            else:
                close = None
                finish = tokens[index - 1][2]
            result.append(dict(words=words, children=children, start=start, end=finish, close=close))
        if nested:
            raise ValueError('Unclosed Nginx block')
        return result

    return block()


def expanded(nodes, documents, prefix, seen=()):
    result = []
    for node in nodes:
        words = node['words']
        if words[0] != 'include':
            result.append(node)
            continue
        if len(words) != 2 or '$' in words[1]:
            raise ValueError('Dynamic Nginx include is unsupported; no configuration changed')
        pattern = Path(words[1])
        if not pattern.is_absolute():
            pattern = prefix / pattern
        paths = glob.glob(str(pattern))
        if not paths and not glob.has_magic(str(pattern)):
            raise ValueError('Nginx include could not be inspected')
        for path in paths:
            key = str(Path(path).resolve())
            if key in seen or key not in documents:
                raise ValueError('Nginx include is recursive or outside the inspected configuration')
            result.extend(expanded(parse(documents[key]), documents, prefix, seen + (key,)))
    return result


def values(nodes, name):
    return [n['words'][1:] for n in nodes if n['words'][0] == name]


def serves_tls(nodes, host, port):
    names = [name for group in values(nodes, 'server_name') for name in group]
    if host not in names:
        return False
    for listen in values(nodes, 'listen'):
        if listen and listen[0].rsplit(':', 1)[-1] == str(port) and 'ssl' in listen[1:]:
            return True
    return False


def plan(documents, prefix, url, upstream, project):
    endpoint = urlsplit(url)
    path = endpoint.path or '/'
    if not re.fullmatch(r'/[A-Za-z0-9/_.~-]*', path) or path == '/':
        raise ValueError('Nginx deployment requires a dedicated URL path, such as /network')
    if not re.fullmatch(r'127\.0\.0\.1:[0-9]+', upstream):
        raise ValueError('Nginx upstream must be the private loopback gateway')
    if not re.fullmatch(r'[a-f0-9]{64}', project):
        raise ValueError('Invalid project ownership marker')
    candidates = []
    for file, text in documents.items():
        def visit(nodes):
            for node in nodes:
                if node['words'] == ['server'] and node['children'] is not None:
                    children = expanded(node['children'], documents, prefix)
                    if serves_tls(children, endpoint.hostname, endpoint.port or 443):
                        candidates.append((file, text, node, children))
                elif node['children'] is not None:
                    visit(node['children'])
        visit(parse(text))
    if len(candidates) != 1:
        raise ValueError('Expected one existing Nginx TLS vhost for the WSS hostname/port; configure its certificate first')
    file, text, server, children = candidates[0]
    if values(children, 'return') or values(children, 'rewrite') or any(n['words'][0] == 'if' for n in children):
        raise ValueError('Nginx vhost has server-level routing rules; refusing automatic changes')
    locations = [n for n in children if n['words'][0] == 'location']
    same = [n for n in locations if n['words'][1:] in (['=', path], [path], ['^~', path])]
    if same:
        # Existing exact routes are reused, never adopted or rewritten.
        if len(same) != 1 or same[0]['words'][1:] != ['=', path]:
            raise ValueError('The WSS path conflicts with an existing Nginx location')
        rules = expanded(same[0]['children'] or [], documents, prefix)
        headers = {v[0].lower(): v[1:] for v in values(rules, 'proxy_set_header') if v}
        if (values(rules, 'proxy_pass') != [['http://' + upstream]]
                or values(rules, 'proxy_http_version') != [['1.1']]
                or headers.get('upgrade') != ['$http_upgrade']
                or headers.get('connection') != ['upgrade']
                or values(rules, 'return') or values(rules, 'rewrite')):
            raise ValueError('Existing WSS route does not target the configured gateway with WebSocket upgrade; refusing to overwrite it')
        return dict(path=file, before=text, after=text)
    marker = '# SceneMax route ' + project
    if marker in text:
        raise ValueError('This project already has a managed Nginx route; retain its URL or remove it explicitly before changing paths')
    addition = ('\n    ' + marker + '\n'
                '    location = ' + path + ' {\n'
                '        proxy_pass http://' + upstream + ';\n'
                '        proxy_http_version 1.1;\n'
                '        proxy_set_header Upgrade $http_upgrade;\n'
                '        proxy_set_header Connection "upgrade";\n'
                '        proxy_set_header Host $host;\n'
                '        proxy_set_header Origin $http_origin;\n'
                '        proxy_read_timeout 3600s;\n'
                '        proxy_send_timeout 3600s;\n'
                '        proxy_buffering off;\n'
                '    }\n')
    return dict(path=file, before=text, after=text[:server['close']] + addition + text[server['close']:])


def replace_checked(plan, reverse=False):
    path = Path(plan['path'])
    before, after = (plan['after'], plan['before']) if reverse else (plan['before'], plan['after'])
    current = path.read_bytes().decode('utf-8')
    if current == after:
        return
    if current != before:
        raise ValueError('Nginx configuration changed concurrently; refusing to overwrite it')
    stat = path.stat()
    fd, temporary = tempfile.mkstemp(prefix='.scenemax-', dir=str(path.parent))
    try:
        with os.fdopen(fd, 'wb') as out:
            out.write(after.encode('utf-8'))
            out.flush()
            os.fsync(out.fileno())
        os.chmod(temporary, stat.st_mode)
        if hasattr(os, 'chown'):
            os.chown(temporary, stat.st_uid, stat.st_gid)
        os.replace(temporary, path)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


def main(args):
    action, manifest = args[:2]
    if action == 'prepare':
        result = subprocess.run(['nginx', '-T'], stdout=subprocess.PIPE,
                                stderr=subprocess.PIPE, universal_newlines=True, check=True)
        paths = re.findall(r'^# configuration file (/.+):$', result.stdout, re.M)
        if not paths:
            raise ValueError('Cannot inspect the active Nginx configuration')
        documents = {str(Path(p).resolve()): Path(p).read_bytes().decode('utf-8') for p in paths}
        draft = plan(documents, Path(paths[0]).parent, *args[2:])
        Path(manifest).write_text(json.dumps(draft), encoding='utf-8')
        print('Reusing existing Nginx WebSocket route' if draft['before'] == draft['after'] else 'Prepared isolated Nginx WebSocket route')
    else:
        draft = json.loads(Path(manifest).read_text(encoding='utf-8'))
        if action in ('apply', 'rollback'):
            replace_checked(draft, reverse=action == 'rollback')
        elif action == 'changed':
            return int(draft['before'] == draft['after'])
        else:
            raise ValueError('Unknown Nginx deployment action')
    return 0


if __name__ == '__main__':
    try:
        sys.exit(main(sys.argv[1:]))
    except (ValueError, OSError, subprocess.CalledProcessError) as error:
        print('Nginx deployment: ' + str(error), file=sys.stderr)
        sys.exit(1)

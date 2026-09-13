"""Validate Cargo component boundaries without third-party Python packages."""
import argparse
import json
from pathlib import Path
import re
import subprocess
import sys
import unittest

ROOT = Path(__file__).resolve().parents[1]
EXPECTED = {
    'scenemax_ide': 'IDE/app', 'scenemax_ide_core': 'IDE/core',
    'scenemax_ide_services': 'IDE/services', 'scenemax_ide_ui': 'IDE/ui',
    'scenemax_projector_nextgen': 'Projector/app', 'scenemax_assets': 'Common/assets',
    'scenemax_parser': 'Language/parser', 'scenemax_ir': 'Language/ir',
    'scenemax_runtime': 'Engine/runtime', 'scenemax_multiplayer': 'Engine/multiplayer',
    'scenemax_runtime_script_core': 'Engine/scripting',
    'scenemax_runtime_vm_core': 'Engine/vm', 'scenemax_runtime_ui_core': 'Engine/runtime_ui',
}
IDE_ALLOWED = {
    'IDE/core': set(),
    'IDE/ui': set(),
    'IDE/services': {'IDE/core', 'Language/parser', 'Common/assets', 'Engine/runtime_ui'},
    'IDE/app': {'IDE/core', 'IDE/services', 'IDE/ui', 'Common/assets'},
}

def dependency_allowed(source, destination):
    if source in IDE_ALLOWED:
        return destination in IDE_ALLOWED[source]
    allowed = {'Engine': {'Engine', 'Common', 'Language'},
               'Projector': {'Engine', 'Common', 'Language'},
               'Language': {'Language', 'Common'}, 'Common': {'Common'}}
    return destination.split('/')[0] in allowed.get(source.split('/')[0], set())

def validate(metadata):
    errors = []
    packages = {p['name']: p for p in metadata['packages']}
    for name, component in EXPECTED.items():
        package = packages.get(name)
        if package is None:
            errors.append(f'Missing component {name}: {component}')
            continue
        if Path(package['manifest_path']).resolve().parent != (ROOT/component).resolve():
            errors.append(f'{name} must live in {component}')
    for package in packages.values():
        folder = Path(package['manifest_path']).resolve().parent
        try:
            component = folder.relative_to(ROOT).as_posix()
        except ValueError:
            errors.append(f'Workspace member outside the product root: {folder}')
            continue
        if package['name'] not in EXPECTED:
            errors.append(f'New component needs a documented boundary: {package["name"]}')
        for dependency in package['dependencies']:
            if dependency.get('path'):
                try:
                    target = Path(dependency['path']).resolve().relative_to(ROOT).as_posix()
                except ValueError:
                    errors.append(f'{component} has an external path dependency: {dependency["path"]}')
                    continue
                if not dependency_allowed(component, target):
                    errors.append(f'Forbidden dependency: {component} -> {target}')
            if component in {'IDE/core', 'IDE/services', 'Language/parser', 'Language/ir'} and dependency['name'].startswith('bevy'):
                errors.append(f'{component} must remain independent of Bevy')
        if component.startswith('IDE/'):
            manifest = (folder/'Cargo.toml').read_text(encoding='utf-8-sig')
            if not re.search(r'\[lints\]\s+workspace\s*=\s*true', manifest):
                errors.append(f'{component} must inherit workspace lint policy')
    for path in (ROOT/'IDE/core/src').rglob('*.rs'):
        text = path.read_text(encoding='utf-8-sig')
        if re.search(r'\b(?:fs|process|thread)::', text):
            errors.append(f'Domain code must not perform infrastructure operations: {path}')
    return errors

class BoundaryTests(unittest.TestCase):
    def test_core_cannot_depend_on_services(self):
        self.assertFalse(dependency_allowed('IDE/core', 'IDE/services'))
    def test_runtime_cannot_depend_on_ide(self):
        self.assertFalse(dependency_allowed('Engine/runtime', 'IDE/core'))
    def test_widgets_cannot_depend_on_documents(self):
        self.assertFalse(dependency_allowed('IDE/ui', 'IDE/core'))
    def test_application_can_use_services(self):
        self.assertTrue(dependency_allowed('IDE/app', 'IDE/services'))
    def test_designer_adapter_can_use_only_pure_ui_runtime(self):
        self.assertTrue(dependency_allowed('IDE/services', 'Engine/runtime_ui'))
        self.assertFalse(dependency_allowed('IDE/services', 'Engine/runtime'))
    def test_projector_can_use_runtime(self):
        self.assertTrue(dependency_allowed('Projector/app', 'Engine/runtime'))

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--self-test', action='store_true')
    args = parser.parse_args()
    if args.self_test:
        result = unittest.TextTestRunner().run(unittest.defaultTestLoader.loadTestsFromTestCase(BoundaryTests))
        return 0 if result.wasSuccessful() else 1
    result = subprocess.run(['cargo', 'metadata', '--offline', '--no-deps', '--format-version', '1',
                             '--manifest-path', str(ROOT/'Cargo.toml')], cwd=ROOT, capture_output=True, text=True, check=True)
    errors = validate(json.loads(result.stdout))
    for error in errors:
        print(error, file=sys.stderr)
    if errors:
        return 1
    print(f'Architecture checks passed for {len(EXPECTED)} components.')
    return 0

if __name__ == '__main__':
    sys.exit(main())

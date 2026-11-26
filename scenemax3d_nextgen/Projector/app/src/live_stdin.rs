use std::io::{self, BufRead, Write};

use scenemax_runtime::PartialCodeSender;

pub(super) fn read_commands(sender: PartialCodeSender) {
    let stdin = io::stdin();
    let mut input = stdin.lock();
    let mut source = Vec::new();
    let mut in_block = false;
    let mut close_on_error = false;

    loop {
        let start = source.len();
        let eof = match input.read_until(b'\n', &mut source) {
            Ok(count) => count == 0,
            Err(error) => {
                eprintln!("live input: {error}");
                break;
            }
        };
        if eof && !in_block {
            break;
        }
        let status = if eof {
            eprintln!("live input: missing :end");
            b"400\n"
        } else {
            let line = &source[start..];
            let line = line.strip_suffix(b"\n").unwrap_or(line);
            let line = line.strip_suffix(b"\r").unwrap_or(line);
            if in_block {
                if line != b":end" {
                    continue;
                }
                source.truncate(start);
            } else if line == b":begin" || line == b":begin!" {
                close_on_error = line == b":begin!";
                in_block = true;
                source.clear();
                continue;
            }

            let result = String::from_utf8(std::mem::take(&mut source))
                .map_err(|error| error.utf8_error().to_string())
                .and_then(|code| sender.run_partial_code(code, None, close_on_error));
            match result {
                Ok(()) => b"202\n",
                Err(error) => {
                    eprintln!("{error}");
                    b"400\n"
                }
            }
        };
        let mut stdout = io::stdout().lock();
        if stdout
            .write_all(status)
            .and_then(|_| stdout.flush())
            .is_err()
            || eof
        {
            break;
        }
        in_block = false;
        close_on_error = false;
    }
}

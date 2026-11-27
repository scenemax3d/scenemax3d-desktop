use anyhow::{Result, bail};
use scenemax_game_mcp::{self as service, Agent};
use serde_json::{Value, json};
use std::io::{self, BufRead, Write};

fn main() -> Result<()> {
    match std::env::args().nth(1).as_deref() {
        None | Some("serve") => {
            let listener = service::listen()?;
            eprintln!("SceneMax Game MCP listening on {}", service::ADDRESS);
            service::serve(listener)
        }
        Some("stdio") => stdio(),
        _ => bail!("Usage: scenemax_game_mcp [serve|stdio]"),
    }
}

fn tools() -> Value {
    let string = json!({"type":"string"});
    json!({"tools":[
        {"name":"pair","description":"Pair this agent with the game displaying the one-time code.","inputSchema":{"type":"object","properties":{"code":string},"required":["code"],"additionalProperties":false}},
        {"name":"run_partial_code","description":"Schedule one complete SceneMax snippet in the paired game. 202 means scheduled, 400 means rejected.","inputSchema":{"type":"object","properties":{"code":string},"required":["code"],"additionalProperties":false}},
        {"name":"unpair","description":"Release this agent's game connection.","inputSchema":{"type":"object","properties":{},"additionalProperties":false}}
    ]})
}

fn call(params: &Value, agent: &mut Option<Agent>) -> Result<String> {
    let code = || {
        params["arguments"]["code"]
            .as_str()
            .ok_or_else(|| anyhow::anyhow!("code must be a string"))
    };
    match params["name"].as_str() {
        Some("pair") => {
            if agent.is_some() {
                bail!("unpair the current game first");
            }
            *agent = Some(Agent::pair(code()?)?);
            Ok("Paired".into())
        }
        Some("unpair") => {
            *agent = None;
            Ok("Unpaired".into())
        }
        Some("run_partial_code") => {
            let source = code()?;
            anyhow::ensure!(source.len() <= service::MAX_CODE, "snippet exceeds 256 KiB");
            match agent
                .as_mut()
                .ok_or_else(|| anyhow::anyhow!("pair with a game first"))?
                .submit(source)
            {
                Ok(status) if status == "202" => Ok(status),
                Ok(status) => bail!("{status}"),
                Err(error) => {
                    *agent = None;
                    bail!("game disconnected; delivery outcome unknown: {error}")
                }
            }
        }
        _ => bail!("unknown game tool"),
    }
}

fn stdio() -> Result<()> {
    let mut agent = None;
    let mut output = io::stdout().lock();
    for line in io::stdin().lock().lines() {
        let line = line?;
        let response = match serde_json::from_str::<Value>(&line) {
            Err(_) => {
                json!({"jsonrpc":"2.0","id":null,"error":{"code":-32700,"message":"Parse error"}})
            }
            Ok(msg) => {
                if msg.get("id").is_none() {
                    continue;
                }
                let result = match msg["method"].as_str() {
                    Some("initialize") => {
                        json!({"protocolVersion":"2025-06-18","capabilities":{"tools":{}},"serverInfo":{"name":"scenemax-game-mcp","version":env!("CARGO_PKG_VERSION")}})
                    }
                    Some("ping") => json!({}),
                    Some("tools/list") => tools(),
                    Some("tools/call") => {
                        let outcome = call(&msg["params"], &mut agent);
                        json!({"isError":outcome.is_err(),"content":[{"type":"text","text":outcome.unwrap_or_else(|e|e.to_string())}]})
                    }
                    _ => {
                        writeln!(
                            output,
                            "{}",
                            json!({"jsonrpc":"2.0","id":msg["id"],"error":{"code":-32601,"message":"Method not found"}})
                        )?;
                        output.flush()?;
                        continue;
                    }
                };
                json!({"jsonrpc":"2.0","id":msg["id"],"result":result})
            }
        };
        writeln!(output, "{response}")?;
        output.flush()?;
    }
    Ok(())
}

use base64::{Engine, engine::general_purpose::STANDARD};
use scenemax_ide_core::deployment::WebLoading;
use std::{fs, io, path::Path};

fn escape(text: &str) -> String {
    text.replace('&', "&amp;")
        .replace('<', "&lt;")
        .replace('>', "&gt;")
        .replace('"', "&quot;")
        .replace('\'', "&#39;")
}

pub(super) fn render(settings: &WebLoading, project: &Path) -> io::Result<String> {
    let background = if settings.background.trim().is_empty() {
        String::new()
    } else {
        let path = project.join(&settings.background);
        let bytes = fs::read(&path)
            .map_err(|e| io::Error::other(format!("Loading background {}: {e}", path.display())))?;
        let mime = match image::guess_format(&bytes) {
            Ok(image::ImageFormat::Png) => "image/png",
            Ok(image::ImageFormat::Jpeg) => "image/jpeg",
            _ => {
                return Err(io::Error::other(
                    "Loading background must be a PNG or JPEG image.",
                ));
            }
        };
        image::load_from_memory(&bytes)
            .map_err(|e| io::Error::other(format!("Invalid loading background: {e}")))?;
        // Embedded in HTML so the background appears before runtime downloads.
        format!("url(data:{mime};base64,{})", STANDARD.encode(bytes))
    };
    let mut html = String::new();
    let mut remaining = include_str!("index.html");
    while let Some((before, rest)) = remaining.split_once("{{") {
        html.push_str(before);
        let (key, after) = rest
            .split_once("}}")
            .expect("closed loading template token");
        html.push_str(&match key {
            "background" => background.clone(),
            "header" => escape(&settings.header),
            "message" => escape(&settings.message),
            "footer" => escape(&settings.footer),
            _ => unreachable!("unknown loading template token"),
        });
        remaining = after;
    }
    html.push_str(remaining);
    Ok(html)
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn custom_text_is_literal_and_older_settings_keep_defaults() {
        let old: scenemax_ide_core::deployment::Settings = serde_json::from_str("{}").unwrap();
        assert_eq!(old.web_loading.footer, "powered by SceneMax3D");
        let settings = WebLoading {
            header: "<img onerror='x'> {{footer}}".into(),
            message: "\"<&".into(),
            footer: String::new(),
            ..Default::default()
        };
        let html = render(&settings, Path::new(".")).unwrap();
        assert!(html.contains("&lt;img onerror=&#39;x&#39;&gt; {{footer}}"));
        assert!(html.contains("data-label=\"&quot;&lt;&amp;\""));
        assert!(!html.contains("<button"));
        assert!(html.contains("id=\"credit\"></div>"));
    }
    #[test]
    fn background_is_embedded_and_missing_images_fail() {
        let root = tempfile::tempdir().unwrap();
        let path = root.path().join("background.png");
        image::RgbImage::new(2, 2).save(&path).unwrap();
        let settings = WebLoading {
            background: "background.png".into(),
            ..Default::default()
        };
        assert!(
            render(&settings, root.path())
                .unwrap()
                .contains("url(data:image/png;base64,")
        );
        fs::remove_file(path).unwrap();
        assert!(render(&settings, root.path()).is_err());
    }
}

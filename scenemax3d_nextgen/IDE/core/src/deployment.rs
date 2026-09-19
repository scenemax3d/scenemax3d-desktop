//! Deployment choices and validation, independent of build tools and the filesystem.
use serde::{Deserialize, Serialize};

/// Supported distribution destinations.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum Target {
    /// A single self-extracting Windows executable.
    Windows,
    /// ZIP containing an executable Linux game.
    Linux,
    /// ZIP containing an executable macOS game.
    Macos,
    /// Android application package supplied by the platform builder.
    Android,
    /// Signed iOS archive supplied by the platform builder.
    Ios,
    /// Browser distribution containing index.html and WebAssembly.
    Web,
}
impl Target {
    /// Stable display order.
    pub const ALL: [Self; 6] = [
        Self::Windows,
        Self::Linux,
        Self::Macos,
        Self::Android,
        Self::Ios,
        Self::Web,
    ];
    /// Stable identifier and default itch channel.
    pub fn id(self) -> &'static str {
        match self {
            Self::Windows => "windows",
            Self::Linux => "linux",
            Self::Macos => "macos",
            Self::Android => "android",
            Self::Ios => "ios",
            Self::Web => "web",
        }
    }
    /// Concise artifact description.
    pub fn label(self) -> &'static str {
        match self {
            Self::Windows => "Windows · single .exe",
            Self::Linux => "Linux · .zip",
            Self::Macos => "macOS · .zip",
            Self::Android => "Android · .apk",
            Self::Ios => "iOS · .ipa",
            Self::Web => "Web · HTML / Wasm .zip",
        }
    }
    /// Whether the native projector and launcher can produce this artifact.
    pub fn desktop(self) -> bool {
        matches!(self, Self::Windows | Self::Linux | Self::Macos)
    }
    /// Default native Rust architecture; users may supply an ARM64 triple instead.
    pub fn triple(self) -> &'static str {
        match self {
            Self::Windows if cfg!(all(target_os = "windows", target_arch = "aarch64")) => {
                "aarch64-pc-windows-msvc"
            }
            Self::Linux if cfg!(target_arch = "aarch64") => "aarch64-unknown-linux-gnu",
            Self::Macos if cfg!(all(target_os = "macos", target_arch = "x86_64")) => {
                "x86_64-apple-darwin"
            }
            Self::Windows => "x86_64-pc-windows-msvc",
            Self::Linux => "x86_64-unknown-linux-gnu",
            Self::Macos => "aarch64-apple-darwin",
            _ => "",
        }
    }
}

/// One platform's build and upload settings. No credentials are serialized.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Platform {
    /// Destination.
    pub target: Target,
    /// Include this platform in the next build.
    pub selected: bool,
    /// Rust target triple for native builds.
    pub triple: String,
    /// Optional prebuilt projector; its adjacent dynamic libraries are included.
    pub runtime: String,
    /// Optional explicit SDK build recipe for Android, iOS or Web.
    pub recipe: String,
    /// itch.io channel.
    pub channel: String,
}

/// Serializable non-secret package form.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(default)]
pub struct Settings {
    /// Filesystem-safe artifact stem.
    pub name: String,
    /// Human-readable release version passed to Butler.
    pub version: String,
    /// Output parent directory; each build receives its own child directory.
    pub output: String,
    /// Optional shared resource directory embedded alongside project resources.
    pub builtin_resources: String,
    /// Build the native Effekseer bridge with the projector.
    pub native_effects: bool,
    /// Explicitly rebuild the engine instead of reusing the installed release binaries.
    pub rebuild_runtime: bool,
    /// Preserve every resource for projects using dynamically constructed asset names.
    pub include_all_resources: bool,
    /// Upload only after all selected artifacts have been built successfully.
    pub upload: bool,
    /// itch.io page URL or user/game.
    pub itch_page: String,
    /// Butler executable, or `butler` on PATH.
    pub butler: String,
    /// All six target configurations.
    pub platforms: Vec<Platform>,
}
impl Default for Settings {
    fn default() -> Self {
        Self {
            name: "game".into(),
            version: "1.0.0".into(),
            output: String::new(),
            builtin_resources: String::new(),
            native_effects: cfg!(windows),
            rebuild_runtime: false,
            include_all_resources: false,
            upload: false,
            itch_page: String::new(),
            butler: "butler".into(),
            platforms: Target::ALL
                .into_iter()
                .map(|target| Platform {
                    target,
                    selected: target
                        == if cfg!(target_os = "macos") {
                            Target::Macos
                        } else if cfg!(target_os = "linux") {
                            Target::Linux
                        } else {
                            Target::Windows
                        },
                    triple: target.triple().into(),
                    runtime: String::new(),
                    recipe: String::new(),
                    channel: target.id().into(),
                })
                .collect(),
        }
    }
}
impl Settings {
    /// Validate before any build or upload is scheduled.
    pub fn validate(&self) -> Result<(), String> {
        if !slug(&self.name) || self.name.len() > 80 {
            return Err(
                "Game filename must contain only letters, numbers, '-' or '_' (1–80 characters)."
                    .into(),
            );
        }
        if self.output.trim().is_empty() {
            return Err("Choose an output directory.".into());
        }
        if self.version.trim().is_empty() || self.version.chars().any(char::is_control) {
            return Err("Enter a release version without control characters.".into());
        }
        if !self.platforms.iter().any(|p| p.selected) {
            return Err("Select at least one platform.".into());
        }
        for target in Target::ALL {
            if self.platforms.iter().filter(|p| p.target == target).count() != 1 {
                return Err("Each platform must occur exactly once.".into());
            }
        }
        for p in self.platforms.iter().filter(|p| p.selected) {
            if !slug(&p.channel) {
                return Err(format!("Enter a valid {} upload channel.", p.target.id()));
            }
            if p.target.desktop()
                && (!slug(&p.triple)
                    || !p.triple.contains(match p.target {
                        Target::Windows => "windows",
                        Target::Linux => "linux",
                        _ => "apple-darwin",
                    }))
            {
                return Err(format!(
                    "Choose a matching Rust target triple for {}.",
                    p.target.id()
                ));
            }
            if !p.target.desktop() && p.recipe.trim().is_empty() {
                return Err(format!(
                    "{} needs a platform build recipe. See IDE/DEPLOYMENT.md for the SDK builder contract.",
                    p.target.label()
                ));
            }
        }
        if self.upload {
            normalize_itch_target(&self.itch_page)?;
            if self.butler.trim().is_empty() {
                return Err("Choose the Butler executable.".into());
            }
        }
        Ok(())
    }
}
fn slug(s: &str) -> bool {
    !s.is_empty()
        && s.bytes()
            .all(|b| b.is_ascii_alphanumeric() || b == b'-' || b == b'_')
}
/// Convert an itch page URL or user/game into a safe Butler destination.
pub fn normalize_itch_target(input: &str) -> Result<String, String> {
    let text = input.trim().trim_end_matches('/').to_ascii_lowercase();
    let result = if let Some(url) = text
        .strip_prefix("https://")
        .or_else(|| text.strip_prefix("http://"))
    {
        let (host, game) = url
            .split_once('/')
            .ok_or("Use https://username.itch.io/game or username/game.")?;
        let user = host
            .strip_suffix(".itch.io")
            .ok_or("The game page must be on itch.io.")?;
        format!("{user}/{game}")
    } else {
        text
    };
    let (user, game) = result
        .split_once('/')
        .ok_or("Use username/game or an itch.io game page URL.")?;
    if !slug(user) || !slug(game) {
        return Err("The itch.io target must contain one username and one game slug.".into());
    }
    Ok(result)
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn itch_targets_are_normalized_and_strict() {
        assert_eq!(
            normalize_itch_target("https://User.itch.io/my-game/").unwrap(),
            "user/my-game"
        );
        for s in [
            "https://evil.test/a",
            "user/game:other",
            "user/a/b",
            "--help",
            "https://u.itch.io/g?q=1",
        ] {
            assert!(normalize_itch_target(s).is_err());
        }
    }
    #[test]
    fn selection_and_recipes_are_required() {
        let mut s = Settings {
            output: "out".into(),
            ..Default::default()
        };
        assert!(s.validate().is_ok());
        s.platforms[3].selected = true;
        assert!(s.validate().unwrap_err().contains("recipe"));
        s.platforms[3].recipe = "android.json".into();
        assert!(s.validate().is_ok());
        s.name = "../game".into();
        assert!(s.validate().is_err());
    }
}

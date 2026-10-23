//! Bevy reads the same preloaded files as scripts and effects, including aliases.
use bevy::asset::io::{AssetReader, AssetReaderError, PathStream, Reader, VecReader};
use scenemax_assets::filesystem as fs;
use std::path::{Path, PathBuf};

pub(crate) struct MountedAssets(pub PathBuf);

impl AssetReader for MountedAssets {
    async fn read<'a>(&'a self, path: &'a Path) -> Result<impl Reader + 'a, AssetReaderError> {
        fs::read(self.0.join(path))
            .map(VecReader::new)
            .map_err(|error| {
                if error.kind() == std::io::ErrorKind::NotFound {
                    AssetReaderError::NotFound(path.into())
                } else {
                    error.into()
                }
            })
    }

    async fn read_meta<'a>(&'a self, path: &'a Path) -> Result<impl Reader + 'a, AssetReaderError> {
        let mut meta = path.as_os_str().to_os_string();
        meta.push(".meta");
        fs::read(self.0.join(meta))
            .map(VecReader::new)
            .map_err(|error| {
                if error.kind() == std::io::ErrorKind::NotFound {
                    AssetReaderError::NotFound(path.into())
                } else {
                    error.into()
                }
            })
    }

    async fn read_directory<'a>(
        &'a self,
        path: &'a Path,
    ) -> Result<Box<PathStream>, AssetReaderError> {
        let entries = fs::read_dir(self.0.join(path))?
            .map(|entry| {
                entry.and_then(|e| {
                    e.path()
                        .strip_prefix(&self.0)
                        .map(Path::to_path_buf)
                        .map_err(std::io::Error::other)
                })
            })
            .collect::<std::io::Result<Vec<_>>>()?;
        Ok(Box::new(bevy::tasks::futures_lite::stream::iter(entries)))
    }

    async fn is_directory<'a>(&'a self, path: &'a Path) -> Result<bool, AssetReaderError> {
        Ok(fs::metadata(self.0.join(path))?.is_dir())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use bevy::tasks::futures_lite::{StreamExt, future::block_on};

    #[test]
    fn reader_preserves_relative_paths_and_reports_missing_files() {
        let root = tempfile::tempdir().unwrap();
        std::fs::create_dir(root.path().join("textures")).unwrap();
        std::fs::write(root.path().join("textures/color.png"), b"texture").unwrap();
        let source = MountedAssets(root.path().to_path_buf());
        block_on(async {
            let mut reader = source.read(Path::new("textures/color.png")).await.unwrap();
            let mut bytes = Vec::new();
            reader.read_to_end(&mut bytes).await.unwrap();
            assert_eq!(bytes, b"texture");
            assert!(source.is_directory(Path::new("textures")).await.unwrap());
            let mut directory = source.read_directory(Path::new("textures")).await.unwrap();
            assert_eq!(
                directory.next().await.unwrap(),
                PathBuf::from("textures/color.png")
            );
            assert!(matches!(
                source.read(Path::new("missing.png")).await,
                Err(AssetReaderError::NotFound(_))
            ));
            assert!(matches!(
                source.read_meta(Path::new("textures/color.png")).await,
                Err(AssetReaderError::NotFound(_))
            ));
        });
    }
}

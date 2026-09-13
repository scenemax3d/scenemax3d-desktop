//! Scoped asset source supporting project switches and relative glTF dependencies.
use bevy::{
    asset::{
        AssetPath,
        io::{AssetReader, AssetReaderError, PathStream, Reader, VecReader, file::FileAssetReader},
    },
    prelude::*,
};
use std::{
    path::{Component, Path, PathBuf},
    sync::{Arc, RwLock},
};

#[derive(Resource, Clone, Default)]
pub(crate) struct ProjectAssets(Arc<RwLock<Vec<PathBuf>>>);

impl ProjectAssets {
    pub(crate) fn asset(&self, root: &Path, path: &Path) -> Option<AssetPath<'static>> {
        let relative = path.strip_prefix(root).ok()?;
        let mut roots = self.0.write().ok()?;
        let id = match roots.iter().position(|r| r == root) {
            Some(id) => id,
            None => {
                roots.push(root.into());
                roots.len() - 1
            }
        };
        Some(AssetPath::from(PathBuf::from(id.to_string()).join(relative)).with_source("project"))
    }

    fn resolve(&self, path: &Path) -> Result<PathBuf, AssetReaderError> {
        let missing = || AssetReaderError::NotFound(path.into());
        let mut parts = path.components();
        let Some(Component::Normal(id)) = parts.next() else {
            return Err(missing());
        };
        let id: usize = id
            .to_str()
            .and_then(|id| id.parse().ok())
            .ok_or_else(missing)?;
        let roots = self.0.read().map_err(|_| missing())?;
        let root = roots.get(id).ok_or_else(missing)?;
        let relative = parts.as_path();
        if relative
            .components()
            .any(|p| !matches!(p, Component::Normal(_) | Component::CurDir))
        {
            return Err(missing());
        }
        let resolved = root.join(relative).canonicalize().map_err(|_| missing())?;
        if !resolved.starts_with(root) {
            return Err(missing());
        }
        Ok(resolved)
    }
}

impl AssetReader for ProjectAssets {
    async fn read<'a>(&'a self, path: &'a Path) -> Result<impl Reader + 'a, AssetReaderError> {
        let resolved = self.resolve(path)?;
        let files = FileAssetReader::new("");
        let mut reader = files.read(&resolved).await?;
        let mut bytes = Vec::new();
        reader.read_to_end(&mut bytes).await?;
        Ok(VecReader::new(bytes))
    }
    async fn read_meta<'a>(&'a self, path: &'a Path) -> Result<impl Reader + 'a, AssetReaderError> {
        Err::<VecReader, _>(AssetReaderError::NotFound(path.into()))
    }
    async fn read_directory<'a>(
        &'a self,
        path: &'a Path,
    ) -> Result<Box<PathStream>, AssetReaderError> {
        Err(AssetReaderError::NotFound(path.into()))
    }
    async fn is_directory<'a>(&'a self, path: &'a Path) -> Result<bool, AssetReaderError> {
        Ok(self.resolve(path)?.is_dir())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn project_sources_keep_identity_and_reject_escape() {
        let a = tempfile::tempdir().unwrap();
        let b = tempfile::tempdir().unwrap();
        std::fs::write(a.path().join("model.gltf"), "a").unwrap();
        std::fs::write(b.path().join("model.gltf"), "b").unwrap();
        let root_a = a.path().canonicalize().unwrap();
        let root_b = b.path().canonicalize().unwrap();
        let source = ProjectAssets::default();
        let asset_a = source.asset(&root_a, &root_a.join("model.gltf")).unwrap();
        let asset_b = source.asset(&root_b, &root_b.join("model.gltf")).unwrap();
        assert_ne!(asset_a, asset_b);
        assert_eq!(
            source.resolve(asset_a.path()).unwrap(),
            root_a.join("model.gltf")
        );
        assert!(source.resolve(Path::new("0/../outside")).is_err());
        assert!(source.resolve(Path::new("999/model.gltf")).is_err());
    }
}

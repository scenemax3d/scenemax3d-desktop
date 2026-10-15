//! Shared script collection identity and explicit JSON snapshot boundaries.
#![forbid(unsafe_code)]
use std::collections::HashMap;
use serde_json::Value;
use std::sync::{Arc, RwLock};

#[derive(Debug, Clone)]
pub struct Reference(Arc<RwLock<Node>>);
#[derive(Debug)]
enum Node { Scalar(Value), Entity(EntityIdentity), Pool(EntityIdentity), Controller(EntityIdentity), Array(Vec<Reference>), Object(HashMap<String, Reference>) }

/// Engine-independent identity. Runtime names are lookup addresses, not identity:
/// redeclaring the same name must produce a different key.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct EntityIdentity { pub key: u64, pub runtime_name: String }

impl PartialEq for Reference { fn eq(&self, other: &Self) -> bool { Arc::ptr_eq(&self.0, &other.0) } }
impl Reference {
    pub fn new(value: Value) -> Self {
        Self(Arc::new(RwLock::new(match value {
            Value::Array(values) => Node::Array(values.into_iter().map(Self::new).collect()),
            Value::Object(values) => Node::Object(values.into_iter().map(|(k,v)| (k,Self::new(v))).collect()),
            value => Node::Scalar(value),
        })))
    }
    pub fn entity(runtime_name: impl Into<String>) -> Self {
        static NEXT: std::sync::atomic::AtomicU64 = std::sync::atomic::AtomicU64::new(1);
        Self(Arc::new(RwLock::new(Node::Entity(EntityIdentity {
            key: NEXT.fetch_add(1, std::sync::atomic::Ordering::Relaxed),
            runtime_name: runtime_name.into(),
        }))))
    }
    pub fn pool(runtime_name: impl Into<String>) -> Self {
        static NEXT: std::sync::atomic::AtomicU64 = std::sync::atomic::AtomicU64::new(1);
        Self(Arc::new(RwLock::new(Node::Pool(EntityIdentity {
            key: NEXT.fetch_add(1, std::sync::atomic::Ordering::Relaxed),
            runtime_name: runtime_name.into(),
        }))))
    }
    pub fn controller(runtime_name: impl Into<String>) -> Self {
        static NEXT: std::sync::atomic::AtomicU64 = std::sync::atomic::AtomicU64::new(1);
        Self(Arc::new(RwLock::new(Node::Controller(EntityIdentity {
            key: NEXT.fetch_add(1, std::sync::atomic::Ordering::Relaxed), runtime_name: runtime_name.into(),
        }))))
    }
    pub fn controller_identity(&self) -> Option<EntityIdentity> {
        match &*self.0.read().ok()? { Node::Controller(value) => Some(value.clone()), _ => None }
    }
    pub fn pool_identity(&self) -> Option<EntityIdentity> {
        match &*self.0.read().ok()? { Node::Pool(value) => Some(value.clone()), _ => None }
    }
    pub fn opaque(&self) -> bool { self.entity_identity().is_some() || self.pool_identity().is_some() || self.controller_identity().is_some() }
    pub fn entity_identity(&self) -> Option<EntityIdentity> {
        match &*self.0.read().ok()? { Node::Entity(value) => Some(value.clone()), _ => None }
    }
    /// Invalidate a retired instance's lookup address for every retained alias.
    /// Its identity key remains stable for equality and collection membership.
    pub fn retire_entity(&self) {
        if let Ok(mut node) = self.0.write() {
            if let Node::Entity(identity) = &mut *node {
                identity.runtime_name = format!("__scenemax_retired_entity_{}", identity.key);
            }
        }
    }
    pub fn has_identity(&self) -> bool { self.collection() || self.opaque() }
    pub fn array(values: Vec<Self>) -> Self { Self(Arc::new(RwLock::new(Node::Array(values)))) }
    pub fn snapshot(&self) -> Option<Value> { self.snapshot_at(0) }
    fn snapshot_at(&self, depth: usize) -> Option<Value> {
        if depth > 128 { return None; }
        Some(match &*self.0.read().ok()? {
            Node::Scalar(value) => value.clone(),
            Node::Entity(_) | Node::Pool(_) | Node::Controller(_) => { if depth == 0 { return None; } Value::Null },
            Node::Array(values) => Value::Array(values.iter().map(|v| v.snapshot_at(depth+1)).collect::<Option<_>>()?),
            Node::Object(values) => Value::Object(values.iter().map(|(k,v)| Some((k.clone(),v.snapshot_at(depth+1)?))).collect::<Option<_>>()?),
        })
    }
    pub fn collection(&self) -> bool { self.0.read().is_ok_and(|n| matches!(*n, Node::Array(_) | Node::Object(_))) }
    pub fn field(&self, index: &Value) -> Option<Self> {
        match &*self.0.read().ok()? {
            Node::Array(values) if index.as_str().is_some_and(|name| name.eq_ignore_ascii_case("length")) => Some(Self::new(serde_json::json!(values.len()))),
            Node::Array(values) => values.get(array_index(index)?).cloned(),
            Node::Object(values) => values.get(index.as_str()?).cloned(),
            Node::Scalar(Value::String(value)) if index.as_str().is_some_and(|name| name.eq_ignore_ascii_case("length")) => Some(Self::new(serde_json::json!(value.encode_utf16().count()))),
            _ => None,
        }
    }
    pub fn is_array(&self) -> bool { self.0.read().is_ok_and(|n| matches!(*n, Node::Array(_))) }
    pub fn is_null(&self) -> bool { self.0.read().is_ok_and(|n| matches!(*n, Node::Scalar(Value::Null))) }
    pub fn entries(&self) -> Option<HashMap<String, Self>> {
        match &*self.0.read().ok()? { Node::Object(values) => Some(values.clone()), _ => None }
    }
    pub fn elements(&self) -> Option<Vec<Self>> { match &*self.0.read().ok()? { Node::Array(v) => Some(v.clone()), _ => None } }
    pub fn push(&self, value: Self) -> Option<()> {
        if !self.can_insert(&value) { return None; }
        let mut node = self.0.write().ok()?;
        let Node::Array(items) = &mut *node else { return None; };
        items.push(value); Some(())
    }
    pub fn fill(&self, value: Self) -> Option<()> {
        if !self.can_insert(&value) { return None; }
        let mut node = self.0.write().ok()?;
        let Node::Array(items) = &mut *node else { return None; };
        items.fill(value); Some(())
    }
    pub fn pop(&self) -> Option<()> {
        let mut node = self.0.write().ok()?;
        let Node::Array(items) = &mut *node else { return None; };
        items.pop(); Some(())
    }
    pub fn clear(&self) -> Option<()> {
        let mut node = self.0.write().ok()?;
        let Node::Array(items) = &mut *node else { return None; };
        items.clear(); Some(())
    }
    fn reaches(&self, target: &Self, seen: &mut std::collections::HashSet<usize>) -> bool {
        if self == target { return true; }
        if !seen.insert(Arc::as_ptr(&self.0) as usize) { return false; }
        let children = match self.0.read() {
            Ok(node) => match &*node { Node::Array(v) => v.clone(), Node::Object(v) => v.values().cloned().collect(), _ => vec![] },
            Err(_) => return true,
        };
        children.iter().any(|v| v.reaches(target, seen))
    }
    fn can_insert(&self, value: &Self) -> bool { !value.reaches(self, &mut Default::default()) }
    pub fn set(&self, index: &Value, value: Self) -> Option<()> {
        if !self.can_insert(&value) { return None; }
        match &mut *self.0.write().ok()? {
            Node::Array(values) => *values.get_mut(array_index(index)?)? = value,
            Node::Object(values) => *values.get_mut(index.as_str()?)? = value,
            _ => return None,
        }
        Some(())
    }
}
fn array_index(value: &Value) -> Option<usize> {
    let n = value.as_f64().or_else(|| value.as_bool().map(|v| if v {1.} else {0.}))?;
    (n >= 0. && n.fract() == 0.).then_some(n as usize)
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn collection_length_accepts_script_capitalization() {
        let array = Reference::new(serde_json::json!([1, 2]));
        for name in ["length", "Length", "LENGTH"] {
            assert_eq!(array.field(&Value::from(name)).unwrap().snapshot(), Some(serde_json::json!(2)));
        }
        let object = Reference::new(serde_json::json!({"Length": 7, "length": 8}));
        assert_eq!(object.field(&Value::from("Length")).unwrap().snapshot(), Some(serde_json::json!(7)));
    }

    #[test]
    fn entity_identity_survives_collections_but_is_not_a_network_string() {
        let first = Reference::entity("subject");
        let replacement = Reference::entity("subject");
        assert_ne!(first.entity_identity(), replacement.entity_identity());
        assert_ne!(first, replacement);
        assert!(first.snapshot().is_none());
        assert!(!first.is_null() && !first.collection());
        let list = Reference::array(vec![first.clone(), Reference::array(vec![replacement.clone()])]);
        assert_eq!(list.field(&Value::from(0)).unwrap(), first);
        assert_eq!(list.snapshot(), Some(serde_json::json!([null, [null]])));
        assert_eq!(list.elements().unwrap()[1].elements().unwrap()[0], replacement);
    }
}

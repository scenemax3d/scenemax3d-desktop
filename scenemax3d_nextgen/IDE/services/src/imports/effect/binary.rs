//! Bounded reader for the official EFKE INFO resource table and legacy SKFE header.
use super::*;
struct Reader<'a> {
    bytes: &'a [u8],
    pos: usize,
}
impl<'a> Reader<'a> {
    fn take(&mut self, n: usize) -> io::Result<&'a [u8]> {
        let end = self
            .pos
            .checked_add(n)
            .ok_or_else(|| error("Effect length overflow"))?;
        let b = self
            .bytes
            .get(self.pos..end)
            .ok_or_else(|| error("Truncated effect file"))?;
        self.pos = end;
        Ok(b)
    }
    fn int(&mut self) -> io::Result<u32> {
        Ok(u32::from_le_bytes(
            self.take(4)?
                .try_into()
                .map_err(|_| error("Invalid effect integer"))?,
        ))
    }
    fn count(&mut self) -> io::Result<u32> {
        let n = self.int()?;
        if n > 1024 {
            return Err(error("Invalid effect resource count"));
        }
        Ok(n)
    }
    fn path(&mut self) -> io::Result<String> {
        let n = self.int()? as usize;
        if n == 0 || n > 32768 {
            return Err(error("Invalid effect resource path"));
        }
        let b = self.take(n * 2)?;
        let u: Vec<_> = b
            .chunks_exact(2)
            .map(|c| u16::from_le_bytes([c[0], c[1]]))
            .collect();
        if u.last() != Some(&0) {
            return Err(error("Unterminated effect resource path"));
        }
        String::from_utf16(&u[..n - 1])
            .map(|s| s.replace('\\', "/"))
            .map_err(|_| error("Invalid UTF-16 resource path"))
    }
    fn array(
        &mut self,
        kind: &'static str,
        out: &mut Vec<(&'static str, String)>,
    ) -> io::Result<()> {
        for _ in 0..self.count()? {
            let p = self.path()?;
            if !p.is_empty() {
                out.push((kind, p));
            }
        }
        Ok(())
    }
}
pub(super) fn references(bytes: &[u8]) -> io::Result<(u32, Vec<(&'static str, String)>)> {
    let mut r = Reader { bytes, pos: 0 };
    let magic = r.take(4)?;
    let version = r.int()?;
    let mut out = Vec::new();
    if magic == b"EFKE" {
        let mut info = None;
        let mut runtime = false;
        while r.pos < bytes.len() {
            let tag = r.take(4)?;
            let n = r.int()? as usize;
            let b = r.take(n)?;
            if tag == b"INFO" {
                info = Some(b);
            }
            if tag == b"BIN_" && b.starts_with(b"SKFE") {
                runtime = true;
            }
        }
        if !runtime {
            return Err(error(
                "No runtime data in effect. Export it with Effekseer first.",
            ));
        }
        let mut r = Reader {
            bytes: info.ok_or_else(|| error("Effect contains no resource manifest"))?,
            pos: 0,
        };
        let mut v = r.int()?;
        if v < 1500 {
            v = 0;
            r.pos = 0;
        }
        if v >= 1700 {
            for _ in 0..r.count()? {
                let k = r.int()?;
                let _flags = r.int()?;
                let p = r.path()?;
                out.push((
                    match k {
                        1 => "Texture",
                        2 => "Sound",
                        3 => "Model",
                        4 => "Material",
                        5 => "Curve",
                        _ => "Resource",
                    },
                    p,
                ));
            }
        } else {
            for k in [
                "Color texture",
                "Normal texture",
                "Distortion texture",
                "Model",
                "Sound",
            ] {
                r.array(k, &mut out)?;
            }
            if v >= 1500 {
                r.array("Material", &mut out)?;
            }
            if v >= 1610 {
                r.array("Curve", &mut out)?;
            }
        }
    } else if magic == b"SKFE" {
        r.array("Color texture", &mut out)?;
        if version >= 9 {
            r.array("Normal texture", &mut out)?;
            r.array("Distortion texture", &mut out)?;
        }
        if version >= 1 {
            r.array("Sound", &mut out)?;
        }
        if version >= 6 {
            r.array("Model", &mut out)?;
        }
        if version >= 15 {
            r.array("Material", &mut out)?;
        }
        if version >= 1607 {
            r.array("Curve", &mut out)?;
        }
    } else {
        return Err(error(
            "Select a compiled .efkefc or .efk effect. Export legacy .efkproj files in Effekseer first.",
        ));
    }
    out.sort();
    out.dedup();
    Ok((version, out))
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn rejects_corrupt_and_truncated_inputs() {
        for b in [
            vec![],
            b"EFKE".to_vec(),
            [
                b"EFKE".as_slice(),
                &[0; 4],
                b"INFO",
                &u32::MAX.to_le_bytes(),
            ]
            .concat(),
        ] {
            assert!(references(&b).is_err());
        }
    }
    #[test]
    fn reads_modern_unicode_resources() {
        let mut info = 1700u32.to_le_bytes().to_vec();
        info.extend(1u32.to_le_bytes());
        info.extend(1u32.to_le_bytes());
        info.extend(1u32.to_le_bytes());
        let path: Vec<u16> = "Textures/火.png\0".encode_utf16().collect();
        info.extend((path.len() as u32).to_le_bytes());
        for c in path {
            info.extend(c.to_le_bytes());
        }
        let mut b = b"EFKE".to_vec();
        b.extend(1u32.to_le_bytes());
        b.extend(b"INFO");
        b.extend((info.len() as u32).to_le_bytes());
        b.extend(info);
        b.extend(b"BIN_");
        b.extend(4u32.to_le_bytes());
        b.extend(b"SKFE");
        assert_eq!(
            references(&b).unwrap().1,
            vec![("Texture", "Textures/火.png".into())]
        );
    }
}

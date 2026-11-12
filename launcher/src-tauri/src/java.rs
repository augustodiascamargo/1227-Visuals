//! Finds Java installations and reads their version. Minecraft 1.8.9 with Forge needs Java 8.

use serde::Serialize;
use std::collections::BTreeSet;
use std::path::{Path, PathBuf};
use std::process::Command;

#[derive(Serialize, Clone, Debug)]
#[serde(rename_all = "camelCase")]
pub struct JavaInfo {
    pub path: String,
    pub version: String,
    pub major: u32,
    pub is64: bool,
    pub ok: bool,
}

fn exe_name() -> &'static str {
    if cfg!(target_os = "windows") { "java.exe" } else { "java" }
}

fn candidates() -> BTreeSet<PathBuf> {
    let mut out = BTreeSet::new();
    let exe = exe_name();
    if let Ok(h) = std::env::var("JAVA_HOME") {
        out.insert(PathBuf::from(h).join("bin").join(exe));
    }
    if let Ok(p) = std::env::var("PATH") {
        for dir in std::env::split_paths(&p) {
            let f = dir.join(exe);
            if f.is_file() {
                out.insert(f);
            }
        }
    }
    let mut roots: Vec<PathBuf> = Vec::new();
    if cfg!(target_os = "windows") {
        for var in ["ProgramFiles", "ProgramFiles(x86)", "ProgramW6432"] {
            if let Ok(pf) = std::env::var(var) {
                for vendor in ["Java", "Eclipse Adoptium", "Zulu", "Amazon Corretto", "BellSoft", "Microsoft", "AdoptOpenJDK"] {
                    roots.push(PathBuf::from(&pf).join(vendor));
                }
            }
        }
        // The official launcher's bundled runtime for old versions
        if let Some(local) = dirs::data_local_dir() {
            roots.push(local.join("Packages").join("Microsoft.4297127D64EC6_8wekyb3d8bbwe").join("LocalCache").join("Local").join("runtime"));
        }
        if let Some(mc) = crate::paths::dot_minecraft() {
            roots.push(mc.join("runtime"));
        }
    } else {
        roots.push(PathBuf::from("/usr/lib/jvm"));
        roots.push(PathBuf::from("/Library/Java/JavaVirtualMachines"));
    }
    for root in roots {
        walk(&root, 0, &mut out);
    }
    out
}

/// Looks a few levels down for bin/java(.exe).
fn walk(dir: &Path, depth: u32, out: &mut BTreeSet<PathBuf>) {
    if depth > 4 || !dir.is_dir() {
        return;
    }
    let f = dir.join("bin").join(exe_name());
    if f.is_file() {
        out.insert(f);
        return;
    }
    if let Ok(rd) = std::fs::read_dir(dir) {
        for e in rd.flatten() {
            if e.path().is_dir() {
                walk(&e.path(), depth + 1, out);
            }
        }
    }
}

/// Parses `java -version` output: "1.8.0_392" -> 8, "17.0.9" -> 17.
pub fn parse_major(text: &str) -> Option<(String, u32)> {
    let start = text.find('"')? + 1;
    let end = start + text[start..].find('"')?;
    let v = &text[start..end];
    let mut parts = v.split(|c| c == '.' || c == '_' || c == '-');
    let first: u32 = parts.next()?.parse().ok()?;
    let major = if first == 1 { parts.next()?.parse().ok()? } else { first };
    Some((v.to_string(), major))
}

pub fn probe(path: &Path) -> Option<JavaInfo> {
    let mut cmd = Command::new(path);
    cmd.arg("-version");
    #[cfg(target_os = "windows")]
    {
        use std::os::windows::process::CommandExt;
        cmd.creation_flags(0x0800_0000); // CREATE_NO_WINDOW
    }
    let out = cmd.output().ok()?;
    let text = String::from_utf8_lossy(&out.stderr).to_string() + &String::from_utf8_lossy(&out.stdout);
    let (version, major) = parse_major(&text)?;
    let is64 = text.contains("64-Bit");
    Some(JavaInfo {
        path: path.to_string_lossy().into_owned(),
        version,
        major,
        is64,
        ok: major == 8,
    })
}

pub fn detect() -> Vec<JavaInfo> {
    let mut v: Vec<JavaInfo> = candidates().iter().filter_map(|p| probe(p)).collect();
    v.sort_by_key(|j| (!j.ok, !j.is64, j.path.clone()));
    v
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn versions() {
        assert_eq!(parse_major("java version \"1.8.0_392\"").unwrap().1, 8);
        assert_eq!(parse_major("openjdk version \"17.0.9\" 2023-10-17").unwrap().1, 17);
        assert_eq!(parse_major("openjdk version \"21\" 2023-09-19").unwrap().1, 21);
        assert!(parse_major("garbage").is_none());
    }
}

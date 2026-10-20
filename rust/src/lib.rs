//! The Kotlin-facing API of the Cybermeta Android app.
//!
//! Thin, owned-data wrappers around the cybermeta engine so UniFFI can
//! pass them over JNA: bytes in, bytes and plain records out. The app
//! never touches files from Rust; it reads content URIs in Kotlin and
//! hands the bytes over.

use cybermeta::meta::{audit, scrub, Keep};

uniffi::setup_scaffolding!();

#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum CybermetaError {
    /// The engine refused (unsupported format, damaged file, or a scrub
    /// that would have left something behind).
    #[error("{reason}")]
    Refused { reason: String },
}

impl From<anyhow::Error> for CybermetaError {
    fn from(e: anyhow::Error) -> Self {
        CybermetaError::Refused {
            reason: format!("{e:#}"),
        }
    }
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct Finding {
    /// "low", "medium" or "high".
    pub severity: String,
    /// "location", "person", "device", "time", "software", "text",
    /// "thumbnail", "hidden_data" or "other".
    pub category: String,
    pub source: String,
    pub label: String,
    pub value: String,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct Block {
    pub kind: String,
    pub label: String,
    pub bytes: u64,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct GpsPoint {
    pub latitude: f64,
    pub longitude: f64,
    pub map_url: String,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct Report {
    /// "jpeg", "png", "webp", "heif", "avif", "tiff", "jxl" or "unknown".
    pub format: String,
    pub format_name: String,
    /// Highest severity found; None when the photo is clean.
    pub risk: Option<String>,
    pub findings: Vec<Finding>,
    pub blocks: Vec<Block>,
    pub gps: Option<GpsPoint>,
    pub scrubbable: bool,
    pub damaged: Option<String>,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct Cleaned {
    pub bytes: Vec<u8>,
    pub removed: Vec<Block>,
    pub before: Report,
    pub after: Report,
    pub notes: Vec<String>,
}

#[derive(Debug, Clone, Copy, uniffi::Record)]
pub struct KeepOptions {
    pub icc: bool,
    pub orientation: bool,
    pub date: bool,
}

fn json_str<T: serde_like::Name>(v: T) -> String {
    v.name()
}

/// serde's lowercase/snake_case names, without pulling serde in here.
mod serde_like {
    use cybermeta::meta::audit::{Category, Severity};
    use cybermeta::meta::container::{BlockKind, Format};
    pub trait Name {
        fn name(&self) -> String;
    }
    impl Name for Severity {
        fn name(&self) -> String {
            self.as_str().into()
        }
    }
    impl Name for Category {
        fn name(&self) -> String {
            match self {
                Category::Hidden => "hidden_data".into(),
                other => other.as_str().into(),
            }
        }
    }
    impl Name for Format {
        fn name(&self) -> String {
            format!("{self:?}").to_ascii_lowercase()
        }
    }
    impl Name for BlockKind {
        fn name(&self) -> String {
            self.as_str().into()
        }
    }
}

fn report(a: &audit::Audit) -> Report {
    Report {
        format: json_str(a.format),
        format_name: a.format.as_str().into(),
        risk: a.risk.map(json_str),
        findings: a
            .findings
            .iter()
            .map(|f| Finding {
                severity: json_str(f.severity),
                category: json_str(f.category),
                source: f.source.clone(),
                label: f.label.clone(),
                value: f.value.clone(),
            })
            .collect(),
        blocks: a
            .blocks
            .iter()
            .map(|b| Block {
                kind: json_str(b.kind),
                label: b.label.clone(),
                bytes: b.bytes as u64,
            })
            .collect(),
        gps: a.gps.as_ref().map(|g| GpsPoint {
            latitude: g.latitude,
            longitude: g.longitude,
            map_url: g.map_url.clone(),
        }),
        scrubbable: a.scrubbable,
        damaged: a.damaged.clone(),
    }
}

fn cleaned(o: scrub::Outcome) -> Cleaned {
    Cleaned {
        removed: o
            .removed
            .iter()
            .map(|r| Block {
                kind: json_str(r.kind),
                label: r.label.clone(),
                bytes: r.bytes as u64,
            })
            .collect(),
        before: report(&o.before),
        after: report(&o.after),
        notes: o.notes,
        bytes: o.bytes,
    }
}

/// What this photo gives away.
#[uniffi::export]
pub fn audit_photo(bytes: Vec<u8>) -> Report {
    report(&audit::audit(&bytes))
}

/// Remove everything but the picture and what `keep` allows; the result
/// has been re-audited and is guaranteed clean.
#[uniffi::export]
pub fn scrub_photo(bytes: Vec<u8>, keep: KeepOptions) -> Result<Cleaned, CybermetaError> {
    let keep = Keep {
        icc: keep.icc,
        orientation: keep.orientation,
        date: keep.date,
    };
    Ok(cleaned(scrub::scrub(&bytes, &keep)?))
}

/// Remove location only (EXIF GPS, XMP / IPTC places).
#[uniffi::export]
pub fn strip_location(bytes: Vec<u8>) -> Result<Cleaned, CybermetaError> {
    Ok(cleaned(scrub::strip_location(&bytes)?))
}

/// Full scrub, then a generic camera identity.
#[uniffi::export]
pub fn anonymous_camera(bytes: Vec<u8>) -> Result<Cleaned, CybermetaError> {
    Ok(cleaned(scrub::anonymous_camera(&bytes)?))
}

/// Move every timestamp by `seconds`.
#[uniffi::export]
pub fn shift_dates(bytes: Vec<u8>, seconds: i64) -> Result<Cleaned, CybermetaError> {
    Ok(cleaned(scrub::date_shift(&bytes, seconds)?))
}

/// "+3d", "-2h30m", "90m" → seconds.
#[uniffi::export]
pub fn parse_shift(text: String) -> Result<i64, CybermetaError> {
    Ok(scrub::parse_shift(&text)?)
}

#[uniffi::export]
pub fn human_shift(seconds: i64) -> String {
    scrub::human_shift(seconds)
}

/// The engine's version, for the About screen.
#[uniffi::export]
pub fn engine_version() -> String {
    format!("cybermeta-ffi {} · engine {}", env!("CARGO_PKG_VERSION"), cybermeta_version())
}

fn cybermeta_version() -> &'static str {
    // Kept in step with the cybermeta dependency in Cargo.toml.
    "0.3.0"
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn bad_input_is_an_error_not_a_panic() {
        assert!(scrub_photo(b"not a photo".to_vec(), KeepOptions { icc: true, orientation: true, date: false }).is_err());
        let r = audit_photo(b"not a photo".to_vec());
        assert_eq!(r.format, "unknown");
        assert!(r.findings.is_empty());
        assert_eq!(parse_shift("-2h30m".into()).unwrap(), -9000);
    }
}

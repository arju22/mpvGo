//! JNI bridge between mpvGo (app.arju.mpvgo.webbrowser.adblock.AdBlockBridge) and Brave's adblock-rust.
//!
//! Every entry point catches panics so a malformed URL or list can never take the app down.

use std::collections::HashSet;
use std::panic::{catch_unwind, AssertUnwindSafe};

use adblock::lists::{FilterSet, ParseOptions};
use adblock::request::Request;
use adblock::Engine;
use jni::objects::{JByteArray, JClass, JObject, JObjectArray, JString};
use jni::sys::{jbyteArray, jint, jlong, jobjectArray, jstring};
use jni::JNIEnv;

const _: fn() = || {
    fn assert_send_sync<T: Send + Sync>() {}
    assert_send_sync::<Engine>();
};

fn guard<T>(fallback: T, f: impl FnOnce() -> Option<T>) -> T {
    catch_unwind(AssertUnwindSafe(f)).ok().flatten().unwrap_or(fallback)
}

fn read_string(env: &mut JNIEnv, s: &JString) -> Option<String> {
    if s.is_null() {
        return Some(String::new());
    }
    env.get_string(s).ok().map(Into::into)
}

fn read_string_array(env: &mut JNIEnv, arr: &JObjectArray) -> Option<Vec<String>> {
    if arr.is_null() {
        return Some(Vec::new());
    }
    let len = env.get_array_length(arr).ok()?;
    let mut out = Vec::with_capacity(len as usize);
    for i in 0..len {
        let obj = env.get_object_array_element(arr, i).ok()?;
        let s = JString::from(obj);
        out.push(read_string(env, &s)?);
        env.delete_local_ref(s).ok()?;
    }
    Some(out)
}

fn engine<'a>(ptr: jlong) -> Option<&'a Engine> {
    if ptr == 0 {
        None
    } else {
        Some(unsafe { &*(ptr as *const Engine) })
    }
}

/// Parses filter list texts into one engine and returns its binary snapshot, or null.
#[no_mangle]
pub extern "system" fn Java_app_arju_mpvgo_webbrowser_adblock_AdBlockBridge_nativeCompile(
    mut env: JNIEnv,
    _class: JClass,
    lists: JObjectArray,
) -> jbyteArray {
    guard(std::ptr::null_mut(), || {
        let texts = read_string_array(&mut env, &lists)?;
        let mut set = FilterSet::new(false);
        for text in texts {
            set.add_filter_list(text, ParseOptions::default());
        }
        let bytes = Engine::new_with_filter_set(set).serialize();
        env.byte_array_from_slice(&bytes).ok().map(|a| a.into_raw())
    })
}

/// Loads a snapshot made by nativeCompile. Returns an engine handle, or 0 on failure.
#[no_mangle]
pub extern "system" fn Java_app_arju_mpvgo_webbrowser_adblock_AdBlockBridge_nativeLoad(
    env: JNIEnv,
    _class: JClass,
    snapshot: JByteArray,
) -> jlong {
    guard(0, || {
        let bytes = env.convert_byte_array(&snapshot).ok()?;
        let mut engine = Engine::default();
        engine.deserialize(&bytes).ok()?;
        Some(Box::into_raw(Box::new(engine)) as jlong)
    })
}

#[no_mangle]
pub extern "system" fn Java_app_arju_mpvgo_webbrowser_adblock_AdBlockBridge_nativeFree(
    _env: JNIEnv,
    _class: JClass,
    ptr: jlong,
) {
    if ptr != 0 {
        let _ = catch_unwind(|| drop(unsafe { Box::from_raw(ptr as *mut Engine) }));
    }
}

/// Returns 1 when the request should be blocked, 0 otherwise.
#[no_mangle]
pub extern "system" fn Java_app_arju_mpvgo_webbrowser_adblock_AdBlockBridge_nativeShouldBlock(
    mut env: JNIEnv,
    _class: JClass,
    ptr: jlong,
    url: JString,
    source_url: JString,
    request_type: JString,
) -> jint {
    guard(0, || {
        let engine = engine(ptr)?;
        let url = read_string(&mut env, &url)?;
        let source = read_string(&mut env, &source_url)?;
        let kind = read_string(&mut env, &request_type)?;
        let request = Request::new(&url, &source, &kind, "GET").ok()?;
        Some(engine.check_network_request(&request).should_block() as jint)
    })
}

fn string_array(env: &mut JNIEnv, items: &[String]) -> Option<jobjectArray> {
    let arr = env
        .new_object_array(items.len() as i32, "java/lang/String", JObject::null())
        .ok()?;
    for (i, item) in items.iter().enumerate() {
        let s = env.new_string(item).ok()?;
        env.set_object_array_element(&arr, i as i32, &s).ok()?;
        env.delete_local_ref(s).ok()?;
    }
    Some(arr.into_raw())
}

/// Page-level cosmetic resources: [generichide "1"/"0", hide selectors joined by ",",
/// exceptions joined by "\n", injected script].
#[no_mangle]
pub extern "system" fn Java_app_arju_mpvgo_webbrowser_adblock_AdBlockBridge_nativeCosmetic(
    mut env: JNIEnv,
    _class: JClass,
    ptr: jlong,
    url: JString,
) -> jobjectArray {
    guard(std::ptr::null_mut(), || {
        let engine = engine(ptr)?;
        let url = read_string(&mut env, &url)?;
        let res = engine.url_cosmetic_resources(&url);
        let selectors: Vec<&str> = res.hide_selectors.iter().map(String::as_str).collect();
        let exceptions: Vec<&str> = res.exceptions.iter().map(String::as_str).collect();
        let out = [
            if res.generichide { "1" } else { "0" }.to_string(),
            selectors.join(","),
            exceptions.join("\n"),
            res.injected_script,
        ];
        string_array(&mut env, &out)
    })
}

/// Generic hide selectors for classes and ids seen on the page, joined by ",".
#[no_mangle]
pub extern "system" fn Java_app_arju_mpvgo_webbrowser_adblock_AdBlockBridge_nativeClassIdSelectors(
    mut env: JNIEnv,
    _class: JClass,
    ptr: jlong,
    classes: JObjectArray,
    ids: JObjectArray,
    exceptions: JString,
) -> jstring {
    guard(std::ptr::null_mut(), || {
        let engine = engine(ptr)?;
        let classes = read_string_array(&mut env, &classes)?;
        let ids = read_string_array(&mut env, &ids)?;
        let exceptions: HashSet<String> = read_string(&mut env, &exceptions)?
            .lines()
            .filter(|l| !l.is_empty())
            .map(str::to_owned)
            .collect();
        let selectors = engine.hidden_class_id_selectors(&classes, &ids, &exceptions);
        env.new_string(selectors.join(",")).ok().map(|s| s.into_raw())
    })
}

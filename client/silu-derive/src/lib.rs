use proc_macro::TokenStream;

use syn::{parse_macro_input, ItemImpl};

mod lua_method;

#[proc_macro_attribute]
pub fn lua_helper(_meta: TokenStream, input: TokenStream) -> TokenStream {
    let ast = parse_macro_input!(input as ItemImpl);
    lua_method::expand(&ast).into()
}

//maker only
#[proc_macro_attribute]
pub fn lua_function(_meta: TokenStream, input: TokenStream) -> TokenStream {
    input
}

#[proc_macro_attribute]
pub fn lua_function_mut(_meta: TokenStream, input: TokenStream) -> TokenStream {
    input
}

#[proc_macro_attribute]
pub fn lua_method(_meta: TokenStream, input: TokenStream) -> TokenStream {
    input
}

#[proc_macro_attribute]
pub fn lua_method_mut(_meta: TokenStream, input: TokenStream) -> TokenStream {
    input
}

#[proc_macro_attribute]
pub fn lua_meta_method(_meta: TokenStream, input: TokenStream) -> TokenStream {
    input
}

#[proc_macro_attribute]
pub fn lua_meta_method_mut(_meta: TokenStream, input: TokenStream) -> TokenStream {
    input
}

#[proc_macro_attribute]
pub fn lua_meta_function(_meta: TokenStream, input: TokenStream) -> TokenStream {
    input
}

#[proc_macro_attribute]
pub fn lua_meta_function_mut(_meta: TokenStream, input: TokenStream) -> TokenStream {
    input
}

#[proc_macro_attribute]
pub fn lua_async_function(_meta: TokenStream, input: TokenStream) -> TokenStream {
    input
}

#[proc_macro_attribute]
pub fn lua_async_method(_meta: TokenStream, input: TokenStream) -> TokenStream {
    input
}

#[proc_macro_attribute]
pub fn lua_async_meta_method(_meta: TokenStream, input: TokenStream) -> TokenStream {
    input
}

#[proc_macro_attribute]
pub fn lua_async_meta_function(_meta: TokenStream, input: TokenStream) -> TokenStream {
    input
}
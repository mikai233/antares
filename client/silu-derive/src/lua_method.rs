use std::ops::{Deref, Not};
use std::str::FromStr;

use proc_macro2::{Span, TokenStream};
use quote::{quote, ToTokens};
use strum::{AsRefStr, Display, EnumIter, EnumString, IntoEnumIterator};
use syn::{Attribute, FnArg, ImplItem, ImplItemMethod, Receiver, Type, TypePath};

#[derive(EnumIter, EnumString, Display, AsRefStr)]
#[strum(serialize_all = "snake_case")]
enum LuaMethodType {
    LuaFunction,
    LuaFunctionMut,
    LuaMethod,
    LuaMethodMut,
    LuaMetaMethod,
    LuaMetaMethodMut,
    LuaMetaFunction,
    LuaMetaFunctionMut,
    LuaAsyncFunction,
    LuaAsyncMethod,
    LuaAsynMetaMethod,
    LuaAsyncMetaFunction,
}

pub fn expand(item_impl: &syn::ItemImpl) -> TokenStream {
    let helper_ty = &item_impl.self_ty;
    let mut lua_method = vec![];
    for item in &item_impl.items {
        match item {
            ImplItem::Const(_) => {}
            ImplItem::Method(m) => {
                let lua_attr_macro: Vec<_> = m.attrs.iter().filter(|a|
                    {
                        LuaMethodType::iter().any(|f| { a.path.is_ident(&f.to_string()) })
                    }).collect();
                if lua_attr_macro.is_empty().not() {
                    assert_eq!(lua_attr_macro.len(), 1, "duplicated lua proc_macro_attribute found");
                    let attr = *lua_attr_macro.first().unwrap();
                    let helper_method = add_helper_method(helper_ty, attr, m);
                    lua_method.push(helper_method);
                    //there is a lua proc_macro_attrbute
                }
            }
            ImplItem::Type(_) => {}
            ImplItem::Macro(_) => {}
            ImplItem::Verbatim(_) => {}
            #[cfg_attr(test, deny(non_exhaustive_omitted_patterns))]
            _ => { /* some sane fallback */ }
        }
    }
    let expaned = quote! {
        #item_impl
        impl mlua::prelude::LuaUserData for #helper_ty {
            fn add_methods<'lua, M: mlua::UserDataMethods<'lua, Self>>(_methods: &mut M) {
                #(#lua_method);*
            }
        }
    };
    expaned.to_token_stream()
}

fn add_helper_method(helper_ty: &Box<Type>, attr: &Attribute, impl_method: &ImplItemMethod) -> TokenStream {
    let func_name = &impl_method.sig.ident;
    let lua_func_name = func_name.to_string();
    let method_type = LuaMethodType::from_str(&*attr.path.get_ident().expect("cannot find lua method ident").to_string()).unwrap();
    let mut fn_params = vec![];
    let mut fn_types = vec![];
    let mut receiver: Option<&Receiver> = None;
    let mut lua_ctx: Option<&syn::PatType> = None;
    // let mut lua_meta: Option<&syn::PatType> = None;
    for fn_arg in &impl_method.sig.inputs {
        match fn_arg {
            FnArg::Receiver(r) => {
                receiver = Some(r);
            }
            FnArg::Typed(pt) => {
                if let Type::Reference(tr) = pt.ty.deref() {
                    if let Type::Path(tp) = tr.elem.deref() {
                        match tp.path.segments.last() {
                            None => {}
                            Some(s) => {
                                let is_lua = s.ident.to_string() == "Lua";
                                if is_lua {
                                    lua_ctx = Some(pt);
                                    continue;
                                }
                            }
                        }
                    }
                }
                fn_params.push(pt.pat.clone());
                fn_types.push(pt.ty.clone());
            }
        }
    }
    let base_invoke_name = method_type.as_ref().strip_prefix("lua_").expect("method type not start with lua_");
    let base_invoke_name = format!("add_{}", base_invoke_name);
    let invoke_func = if method_type.as_ref().ends_with("mut") {
        syn::Ident::new(&*format!("{}_mut", base_invoke_name), Span::call_site())
    } else {
        syn::Ident::new(&*base_invoke_name, Span::call_site())
    };
    let expanded = match method_type {
        LuaMethodType::LuaFunction |
        LuaMethodType::LuaFunctionMut => {
            assert!(receiver.is_none(), "lua_function cannot have a self receiver");
            match lua_ctx {
                None => {
                    quote! {
                        _methods.#invoke_func(#lua_func_name, |_, (#(#fn_params),*): (#(#fn_types),*)| {
                            #helper_ty::#func_name(#(#fn_params),*)
                        });
                    }
                }
                Some(_) => {
                    quote! {
                        _methods.#invoke_func(#lua_func_name, |lua, (#(#fn_params),*): (#(#fn_types),*)| {
                            #helper_ty::#func_name(lua, #(#fn_params),*)
                        });
                    }
                }
            }
        }
        LuaMethodType::LuaMethod |
        LuaMethodType::LuaMethodMut => {
            assert!(receiver.is_some(), "lua_method self receiver not found");
            match lua_ctx {
                None => {
                    quote! {
                        _methods.#invoke_func(#lua_func_name, |_, this, (#(#fn_params),*): (#(#fn_types),*)| {
                            this.#func_name(#(#fn_params),*)
                        });
                    }
                }
                Some(_) => {
                    quote! {
                        _methods.#invoke_func(#lua_func_name, |lua, this, (#(#fn_params),*): (#(#fn_types),*)| {
                            this.#func_name(lua, #(#fn_params),*)
                        });
                    }
                }
            }
        }
        LuaMethodType::LuaMetaMethod |
        LuaMethodType::LuaMetaMethodMut => {
            let arg: Type = attr.parse_args().expect("one lua meta enum expect");
            let meta_path: TypePath;
            if let Type::Path(tp) = arg {
                meta_path = tp;
            } else {
                panic!("one lua meta enum expect");
            }
            assert!(receiver.is_some(), "lua_method self receiver not found");
            match lua_ctx {
                None => {
                    quote! {
                        _methods.#invoke_func(#meta_path, |_, this, (#(#fn_params),*): (#(#fn_types),*)| {
                            this.#func_name(#(#fn_params),*)
                        });
                    }
                }
                Some(_) => {
                    quote! {
                        _methods.#invoke_func(#meta_path, |lua, this, (#(#fn_params),*): (#(#fn_types),*)| {
                            this.#func_name(lua, #(#fn_params),*)
                        });
                    }
                }
            }
        }
        LuaMethodType::LuaMetaFunction |
        LuaMethodType::LuaMetaFunctionMut => {
            let arg: Type = attr.parse_args().expect("one lua meta enum expect");
            let meta_path: TypePath;
            if let Type::Path(tp) = arg {
                meta_path = tp;
            } else {
                panic!("one lua meta enum expect");
            }
            assert!(receiver.is_none(), "lua_function cannot have a self receiver");
            match lua_ctx {
                None => {
                    quote! {
                        _methods.#invoke_func(#meta_path, |_, (#(#fn_params),*): (#(#fn_types),*)| {
                            #helper_ty::#func_name(#(#fn_params),*)
                        });
                    }
                }
                Some(_) => {
                    quote! {
                        _methods.#invoke_func(#meta_path, |lua, (#(#fn_params),*): (#(#fn_types),*)| {
                            #helper_ty::#func_name(lua, #(#fn_params),*)
                        });
                    }
                }
            }
        }
        LuaMethodType::LuaAsyncFunction => {
            todo!()
        }
        LuaMethodType::LuaAsyncMethod => {
            todo!()
        }
        LuaMethodType::LuaAsynMetaMethod => {
            todo!()
        }
        LuaMethodType::LuaAsyncMetaFunction => {
            todo!()
        }
    };
    expanded
}
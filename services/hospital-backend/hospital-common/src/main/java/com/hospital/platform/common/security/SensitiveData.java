package com.hospital.platform.common.security;
public final class SensitiveData { private SensitiveData(){} public static String maskPhone(String v){return v==null?null:v.length()<7?"***":v.substring(0,3)+"****"+v.substring(v.length()-4);} public static String maskIdCard(String v){return v==null?null:v.length()<8?"***":v.substring(0,2)+"********"+v.substring(v.length()-4);} }

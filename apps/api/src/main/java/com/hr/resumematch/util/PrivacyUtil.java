package com.hr.resumematch.util;

import lombok.extern.slf4j.Slf4j;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 隐私脱敏工具（PRD R5 合规）。
 * 对手机号、邮箱、身份证号进行掩码处理，HRBP 可展开明文并记操作日志。
 */
@Slf4j
public final class PrivacyUtil {

    /** 手机号: 1[3-9]\d{9} → 138****1234 */
    private static final Pattern PHONE = Pattern.compile("1[3-9]\\d{9}");
    /** 邮箱: xxx@xxx.xx → x***@example.com */
    private static final Pattern EMAIL = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    /** 身份证号: 18位 → 110101****1234 */
    private static final Pattern ID_CARD = Pattern.compile("\\d{17}[\\dXx]");

    private PrivacyUtil() {}

    /**
     * 对文本中的敏感字段进行脱敏。
     */
    public static String maskSensitive(String text) {
        if (text == null || text.isBlank()) return text;
        String result = text;
        result = maskByPattern(result, PHONE, PrivacyUtil::maskPhone);
        result = maskByPattern(result, EMAIL, PrivacyUtil::maskEmail);
        result = maskByPattern(result, ID_CARD, PrivacyUtil::maskIdCard);
        return result;
    }

    /**
     * 脱敏手机号: 13812345678 → 138****5678
     */
    static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return phone;
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /**
     * 脱敏邮箱: zhangsan@example.com → z***@example.com
     */
    static String maskEmail(String email) {
        if (email == null || !email.contains("@")) return email;
        int atIndex = email.indexOf('@');
        String local = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        if (local.length() <= 1) return "*" + domain;
        return local.charAt(0) + "***" + domain;
    }

    /**
     * 脱敏身份证号: 110101199001011234 → 110101********1234
     */
    static String maskIdCard(String idCard) {
        if (idCard == null || idCard.length() < 8) return idCard;
        return idCard.substring(0, 6) + "********" + idCard.substring(idCard.length() - 4);
    }

    private static String maskByPattern(String text, Pattern pattern, java.util.function.Function<String, String> masker) {
        Matcher m = pattern.matcher(text);
        StringBuilder sb = new StringBuilder();
        int lastEnd = 0;
        while (m.find()) {
            sb.append(text, lastEnd, m.start());
            sb.append(masker.apply(m.group()));
            lastEnd = m.end();
        }
        sb.append(text.substring(lastEnd));
        return sb.toString();
    }
}

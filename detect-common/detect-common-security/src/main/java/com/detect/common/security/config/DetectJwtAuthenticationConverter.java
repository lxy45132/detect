package com.detect.common.security.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * JWT -> 认证令牌转换器：优先读取自定义 authorities 声明，回退到标准 scope。
 * principal 名称取 username 声明(缺省取 sub)。
 */
public class DetectJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final JwtGrantedAuthoritiesConverter defaultConverter = new JwtGrantedAuthoritiesConverter();

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = extractAuthorities(jwt);
        String username = jwt.getClaimAsString("username");
        if (username == null) {
            username = jwt.getSubject();
        }
        return new JwtAuthenticationToken(jwt, authorities, username);
    }

    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        List<String> claim = jwt.getClaimAsStringList("authorities");
        if (claim == null || claim.isEmpty()) {
            return defaultConverter.convert(jwt);
        }
        Collection<GrantedAuthority> result = new ArrayList<>(claim.size());
        for (String a : claim) {
            result.add(new SimpleGrantedAuthority(a));
        }
        return result;
    }
}

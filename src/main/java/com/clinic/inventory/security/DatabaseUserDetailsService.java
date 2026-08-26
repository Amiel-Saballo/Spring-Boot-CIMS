package com.clinic.inventory.security;

import com.clinic.inventory.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

@Service
@RequiredArgsConstructor
public class DatabaseUserDetailsService implements UserDetailsService {
    private final UserAccountRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        var account = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown account"));
        var authorities = new ArrayList<SimpleGrantedAuthority>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + account.getRole().getName().toUpperCase().replace(' ', '_')));
        account.getRole().getPermissions().forEach(p -> authorities.add(new SimpleGrantedAuthority("PERM_" + p.getCode())));
        return User.withUsername(account.getEmail())
                .password(account.getPasswordHash())
                .disabled(!account.isActive() || !account.getRole().isActive())
                .authorities(authorities)
                .build();
    }
}

package com.clinic.inventory.repository;
import com.clinic.inventory.entity.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    Optional<UserAccount> findByEmailIgnoreCase(String email);
    boolean existsByRoleIdAndActiveTrue(Long roleId);
    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);
}

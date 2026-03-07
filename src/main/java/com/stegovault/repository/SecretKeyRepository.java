package com.stegovault.repository;

import com.stegovault.entity.SecretKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

// Extending JpaRepository gives us save(), findById(), and deleteById() for free.
// deleteById() is the Kill Switch — it permanently removes a compromised key.
@Repository
public interface SecretKeyRepository extends JpaRepository<SecretKey, String> {
}
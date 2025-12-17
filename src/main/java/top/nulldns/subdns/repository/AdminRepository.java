package top.nulldns.subdns.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import top.nulldns.subdns.dao.Admin;

public interface AdminRepository extends JpaRepository<Admin, Long> {
}

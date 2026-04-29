package ru.bizsupport.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.bizsupport.entity.Deadline;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface DeadlineRepository extends JpaRepository<Deadline, Long> {
    List<Deadline> findByTaxRegimeIdAndCompanyProfileIsNull(Long taxRegimeId);
    List<Deadline> findByCompanyProfileId(Long companyId);

    @Query("SELECT d FROM Deadline d WHERE d.companyProfile.id = :companyId " +
           "AND d.dueDate BETWEEN :from AND :to ORDER BY d.dueDate ASC")
    List<Deadline> findUpcoming(@Param("companyId") Long companyId,
                                @Param("from") LocalDate from,
                                @Param("to") LocalDate to);
}

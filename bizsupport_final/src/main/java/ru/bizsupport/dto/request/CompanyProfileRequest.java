package ru.bizsupport.dto.request;
import jakarta.validation.constraints.*;
import lombok.Data;
import ru.bizsupport.entity.CompanyProfile;
import java.math.BigDecimal;

@Data
public class CompanyProfileRequest {
    @NotBlank
    private String companyName;
    @NotNull
    private CompanyProfile.CompanyType companyType;
    private String inn;
    private String industry;
    private Integer employeesCount;
    private BigDecimal annualRevenue;
}

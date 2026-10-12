package com.restaurant.restaurantpos.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlatformOnboardingRequest {

    @NotBlank(message = "Restaurant name is required")
    @Size(max = 150, message = "Restaurant name cannot exceed 150 characters")
    private String restaurantName;

    private String address;

    @Size(max = 20, message = "Phone cannot exceed 20 characters")
    private String phone;

    @Email(message = "Restaurant email must be valid")
    @Size(max = 150, message = "Email cannot exceed 150 characters")
    private String email;

    @Size(max = 50, message = "GST number cannot exceed 50 characters")
    private String gstNumber;

    @NotBlank(message = "Admin name is required")
    @Size(max = 100, message = "Admin name cannot exceed 100 characters")
    private String adminName;

    @NotBlank(message = "Admin email is required")
    @Email(message = "Admin email must be a valid email address")
    @Size(max = 150, message = "Admin email cannot exceed 150 characters")
    private String adminEmail;

    @NotBlank(message = "Admin password is required")
    @Size(min = 8, max = 100, message = "Admin password must be between 8 and 100 characters long")
    private String adminPassword;

    @Size(max = 20, message = "Admin phone cannot exceed 20 characters")
    private String adminPhone;
}

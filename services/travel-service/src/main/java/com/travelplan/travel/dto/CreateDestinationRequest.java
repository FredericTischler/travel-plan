package com.travelplan.travel.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /destinations}.
 *
 * Validated by {@code @Valid} in the controller. Constraint violations are
 * handled by {@link com.travelplan.travel.exception.GlobalExceptionHandler}
 * and returned as HTTP 400.
 */
public class CreateDestinationRequest {

    @NotBlank(message = "must not be blank")
    private String name;

    @NotBlank(message = "must not be blank")
    private String country;

    public CreateDestinationRequest() {
        // required for Jackson deserialization
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }
}
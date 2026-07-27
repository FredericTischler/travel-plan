package com.travelplan.travel.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code PUT /destinations/{id}}.
 *
 * Full replacement of the mutable fields ({@code name}, {@code country}).
 * Validated by {@code @Valid} in the controller.
 */
public class UpdateDestinationRequest {

    @NotBlank(message = "must not be blank")
    private String name;

    @NotBlank(message = "must not be blank")
    private String country;

    public UpdateDestinationRequest() {
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
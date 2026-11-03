package com.aucolher.api.donation;

import com.aucolher.api.donation.dto.DonationRequestDTO;
import com.aucolher.api.donation.dto.DonationResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class DonationController {

    private final DonationService service;

    @PostMapping("/api/campaigns/{id}/donations")
    @ResponseStatus(HttpStatus.CREATED)
    public DonationResponseDTO donate(@PathVariable Long id,
                                      @RequestBody DonationRequestDTO body,
                                      Authentication auth) {
        return service.create(id, body, auth);
    }

    // rotas fixas (o Spring prefere o caminho literal a /{id})
    @GetMapping("/api/donations/mine")
    public List<DonationResponseDTO> mine(Authentication auth) {
        return service.listMine(auth);
    }

    @GetMapping("/api/donations/received")
    public List<DonationResponseDTO> received(Authentication auth) {
        return service.listReceived(auth);
    }

    @GetMapping("/api/donations/{id}")
    public DonationResponseDTO get(@PathVariable Long id, Authentication auth) {
        return service.getById(id, auth);
    }
}

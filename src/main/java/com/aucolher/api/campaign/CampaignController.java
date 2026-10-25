package com.aucolher.api.campaign;

import com.aucolher.api.campaign.dto.CampaignRequestDTO;
import com.aucolher.api.campaign.dto.CampaignResponseDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/campaigns")
@RequiredArgsConstructor
public class CampaignController {

    private final CampaignService service;

    // ---- públicas
    @GetMapping
    public List<CampaignResponseDTO> list(@RequestParam(required = false) Long ngoId,
                                          @RequestParam(required = false) String status) {
        return service.listPublic(ngoId, status);
    }

    @GetMapping("/{id}")
    public CampaignResponseDTO get(@PathVariable Long id) {
        return service.getById(id);
    }

    // ---- rota fixa (o Spring prefere o caminho literal a /{id})
    @GetMapping("/mine")
    public List<CampaignResponseDTO> mine(Authentication auth) {
        return service.listMine(auth);
    }

    // ---- ONG
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CampaignResponseDTO create(@Valid @RequestBody CampaignRequestDTO body, Authentication auth) {
        return service.create(body, auth);
    }

    @PutMapping("/{id}")
    public CampaignResponseDTO update(@PathVariable Long id,
                                      @Valid @RequestBody CampaignRequestDTO body,
                                      Authentication auth) {
        return service.update(id, body, auth);
    }

    @PostMapping("/{id}/close")
    public CampaignResponseDTO close(@PathVariable Long id, Authentication auth) {
        return service.close(id, auth);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Authentication auth) {
        service.delete(id, auth);
    }
}

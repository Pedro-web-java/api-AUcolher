package com.aucolher.api.donation.dto;

/**
 * A campanha da doação. `removed`: excluída, pela ONG ou pela moderação;
 * `removedByModeration`: quem removeu foi o admin (o histórico avisa o doador).
 */
public record DonationCampaignDTO(Long id, String title, String ngoName, boolean removed,
                                  boolean removedByModeration) {
}

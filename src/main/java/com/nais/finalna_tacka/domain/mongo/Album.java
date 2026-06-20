package com.nais.finalna_tacka.domain.mongo;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * MongoDB document for an album.
 */
@Data
@Document(collection = "albums")
public class Album {

    @Id
    private String id;

    private String title;

    private String artistId;

    private int releaseYear;
}

package com.nais.finalna_tacka.domain.mongo;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

/**
 * MongoDB document for a playlist.
 */
@Data
@Document(collection = "playlists")
public class Playlist {

    @Id
    private String id;

    private String ownerId;

    private String name;

    private List<String> songIds = new ArrayList<>();
}

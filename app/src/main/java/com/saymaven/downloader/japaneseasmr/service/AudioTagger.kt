package com.saymaven.downloader.japaneseasmr.service

import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.ArtworkFactory
import java.io.File

object AudioTagger {

    fun tagAudioFile(
        audioFile: File,
        coverFile: File?,
        title: String,
        artist: String,
        album: String,
        genre: String,
        comment: String = "JapaneseASMR"
    ): Boolean {
        return try {
            val f = AudioFileIO.read(audioFile)
            val tag = f.tagOrCreateAndSetDefault

            tag.setField(FieldKey.TITLE, title)
            tag.setField(FieldKey.ARTIST, if (artist.isBlank() || artist == "-") "JapaneseASMR" else artist)
            tag.setField(FieldKey.ALBUM, if (album.isBlank() || album == "-") "JapaneseASMR" else album)
            tag.setField(FieldKey.GENRE, if (genre.isBlank() || genre == "-") "ASMR" else genre)
            tag.setField(FieldKey.COMMENT, comment)

            if (coverFile != null && coverFile.exists() && coverFile.length() > 0) {
                try {
                    val artwork = ArtworkFactory.createArtworkFromFile(coverFile)
                    tag.deleteArtworkField()
                    tag.setField(artwork)
                } catch (e: Exception) {
                    // Skip cover artwork if format not supported
                }
            }

            f.commit()
            true
        } catch (e: Exception) {
            // Safe fallback: keep audio file intact
            false
        }
    }
}

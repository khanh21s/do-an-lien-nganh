package com.parroto.content.controller;

import com.parroto.content.dto.UploadAudioResponse;
import com.parroto.content.entity.Lesson;
import com.parroto.content.entity.Sentence;
import com.parroto.content.exception.ApiException;
import com.parroto.content.repository.SentenceRepository;
import com.parroto.content.security.AdminGuard;
import com.parroto.content.security.AuthenticatedUser;
import com.parroto.content.service.LessonService;
import com.parroto.content.service.storage.LessonAudioStorage;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
public class AudioController {

    private final LessonService lessonService;
    private final SentenceRepository sentenceRepository;
    private final LessonAudioStorage audioStorage;
    private final AdminGuard adminGuard;

    public AudioController(LessonService lessonService, SentenceRepository sentenceRepository,
                            LessonAudioStorage audioStorage, AdminGuard adminGuard) {
        this.lessonService = lessonService;
        this.sentenceRepository = sentenceRepository;
        this.audioStorage = audioStorage;
        this.adminGuard = adminGuard;
    }

    @GetMapping("/lessons/{id}/audio")
    public void streamLessonAudio(@PathVariable UUID id, HttpServletRequest request, HttpServletResponse response) {
        Lesson lesson = lessonService.findActiveLesson(id);
        audioStorage.stream(lesson.getAudioUrl(), request.getHeader("Range"), response);
    }

    @GetMapping("/lessons/{id}/audio/download")
    public void downloadLessonAudio(@PathVariable UUID id, HttpServletResponse response) {
        Lesson lesson = lessonService.findActiveLesson(id);
        audioStorage.download(lesson.getAudioUrl(), response);
    }

    @PostMapping(value = "/lessons/{id}/upload-audio", consumes = "multipart/form-data")
    public UploadAudioResponse uploadLessonAudio(@AuthenticationPrincipal AuthenticatedUser principal,
                                                  @PathVariable UUID id,
                                                  @RequestParam("file") MultipartFile file,
                                                  @RequestParam(required = false) Integer duration) {
        adminGuard.requireAdmin(principal);
        return lessonService.uploadLessonAudio(id, file, duration);
    }

    @GetMapping("/sentences/{id}/audio")
    public void streamSentenceAudio(@PathVariable UUID id, HttpServletRequest request, HttpServletResponse response) {
        Sentence sentence = sentenceRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Sentence not found"));
        if (sentence.getAudioUrl() == null) {
            throw ApiException.notFound("Sentence has no dedicated audio file");
        }
        audioStorage.stream(sentence.getAudioUrl(), request.getHeader("Range"), response);
    }
}

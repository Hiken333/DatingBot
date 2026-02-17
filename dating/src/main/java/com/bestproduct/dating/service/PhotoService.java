package com.bestproduct.dating.service;

import com.bestproduct.dating.config.AppConfig;
import com.bestproduct.dating.domain.entity.Profile;
import com.bestproduct.dating.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Сервис для работы с фотографиями через Telegram
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PhotoService {

    private final ProfileRepository profileRepository;
    private final AppConfig appConfig;

    /**
     * Сохранить file_id фотографии в профиль
     * 
     * @param userId ID пользователя
     * @param photoSizes Список размеров фото от Telegram
     * @return file_id самой большой фотографии
     */
    @Transactional
    public String savePhotoToProfile(Long userId, List<PhotoSize> photoSizes) {
        Profile profile = profileRepository.findByUserId(userId)
            .orElseThrow(() -> new IllegalArgumentException("Profile not found"));

        // Получить самую большую фотографию
        PhotoSize largestPhoto = photoSizes.stream()
            .max(Comparator.comparing(PhotoSize::getFileSize))
            .orElseThrow(() -> new IllegalArgumentException("No photos found"));

        String fileId = largestPhoto.getFileId();

        // Проверить лимит фотографий
        List<String> currentPhotos = profile.getPhotoUrls();
        if (currentPhotos.size() >= appConfig.getImages().getMaxPerProfile()) {
            throw new IllegalArgumentException("Maximum photos limit reached: " + 
                appConfig.getImages().getMaxPerProfile());
        }

        // Добавить file_id в список
        currentPhotos.add(fileId);
        profile.setPhotoUrls(currentPhotos);
        profileRepository.save(profile);

        log.info("Photo saved to profile: userId={}, fileId={}", userId, fileId);
        return fileId;
    }

    /**
     * Заменить фотографию в профиле (удалить все старые и добавить новую)
     */
    @Transactional
    public String replacePhotoInProfile(Long userId, List<PhotoSize> photoSizes) {
        Profile profile = profileRepository.findByUserId(userId)
            .orElseThrow(() -> new IllegalArgumentException("Profile not found"));

        // Получить самую большую фотографию
        PhotoSize largestPhoto = photoSizes.stream()
            .max(Comparator.comparing(PhotoSize::getFileSize))
            .orElseThrow(() -> new IllegalArgumentException("No photos found"));

        String fileId = largestPhoto.getFileId();

        // Заменить все фотографии на новую
        List<String> newPhotos = new ArrayList<>();
        newPhotos.add(fileId);
        profile.setPhotoUrls(newPhotos);
        profileRepository.save(profile);

        log.info("Photo replaced in profile: userId={}, fileId={}", userId, fileId);
        return fileId;
    }

    /**
     * Удалить фотографию из профиля
     */
    @Transactional
    public void deletePhotoFromProfile(Long userId, String fileId) {
        Profile profile = profileRepository.findByUserId(userId)
            .orElseThrow(() -> new IllegalArgumentException("Profile not found"));

        List<String> photos = profile.getPhotoUrls();
        photos.remove(fileId);
        profile.setPhotoUrls(photos);
        profileRepository.save(profile);

        log.info("Photo deleted from profile: userId={}, fileId={}", userId, fileId);
    }

    /**
     * Получить все фотографии профиля
     */
    public List<String> getProfilePhotos(Long userId) {
        Profile profile = profileRepository.findByUserId(userId)
            .orElseThrow(() -> new IllegalArgumentException("Profile not found"));
        return profile.getPhotoUrls();
    }

    /**
     * Создать SendPhoto объект для отправки фотографии
     */
    public SendPhoto createSendPhoto(Long chatId, String fileId, String caption) {
        SendPhoto sendPhoto = new SendPhoto();
        sendPhoto.setChatId(chatId.toString());
        sendPhoto.setPhoto(new InputFile(fileId));
        if (caption != null) {
            sendPhoto.setCaption(caption);
        }
        return sendPhoto;
    }

    /**
     * Отправить все фотографии профиля (для показа в свайпах)
     */
    public List<SendPhoto> createProfilePhotoMessages(Long chatId, Profile profile) {
        List<String> fileIds = profile.getPhotoUrls();
        
        return fileIds.stream()
            .map(fileId -> createSendPhoto(chatId, fileId, null))
            .toList();
    }
}




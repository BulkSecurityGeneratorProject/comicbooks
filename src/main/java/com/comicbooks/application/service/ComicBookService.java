package com.comicbooks.application.service;

import com.comicbooks.application.config.StorageProperties;
import com.comicbooks.application.domain.ComicBook;
import com.comicbooks.application.repository.ComicBookRepository;
import com.comicbooks.application.service.dto.ComicBookDTO;
import com.comicbooks.application.service.mapper.ComicBookMapper;
import io.undertow.util.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;


/**
 * Service Implementation for managing ComicBook.
 */
@Service
@Transactional
public class ComicBookService {

    private final Logger log = LoggerFactory.getLogger(ComicBookService.class);

    private final Path storageLocation;

    private final ComicBookRepository comicBookRepository;

    private final ComicBookMapper comicBookMapper;

    public ComicBookService(ComicBookRepository comicBookRepository, StorageProperties storageProperties,
                            ComicBookMapper comicBookMapper) {
        this.comicBookRepository = comicBookRepository;
        this.comicBookMapper = comicBookMapper;
        this.storageLocation = Paths.get(storageProperties.getUploadDir());
        try {
            Files.createDirectories(storageLocation);
        } catch (IOException e) {
            log.error("Could not create the directory where uploaded comic books will be stored");
        }
    }

    /**
     * Save a comicBook.
     *
     * @param comicBookDTO the entity to save
     * @return the persisted entity
     */
    public ComicBookDTO save(ComicBookDTO comicBookDTO) {
        log.debug("Request to save ComicBook : {}", comicBookDTO);
        ComicBook comicBook = comicBookMapper.toEntity(comicBookDTO);
        comicBook = comicBookRepository.save(comicBook);
        return comicBookMapper.toDto(comicBook);
    }

    /**
     * Get all the comicBooks.
     *
     * @param pageable the pagination information
     * @return the list of entities
     */
    @Transactional(readOnly = true)
    public Page<ComicBookDTO> findAll(Pageable pageable) {
        log.debug("Request to get all ComicBooks");
        return comicBookRepository.findAll(pageable)
            .map(comicBookMapper::toDto);
    }

    /**
     * Get one comicBook by id.
     *
     * @param id the id of the entity
     * @return the entity
     */
    @Transactional(readOnly = true)
    public ComicBookDTO findOne(Long id) {
        log.debug("Request to get ComicBook : {}", id);
        ComicBook comicBook = comicBookRepository.findOne(id);
        return comicBookMapper.toDto(comicBook);
    }

    /**
     * Delete the comicBook by id.
     *
     * @param id the id of the entity
     */
    public void delete(Long id) {
        log.debug("Request to delete ComicBook : {}", id);
        comicBookRepository.delete(id);
    }

    public ComicBookDTO uploadComicBook(MultipartFile file, Long id, String type) throws BadRequestException, FileSystemException {
        ComicBookDTO comicBookDTO = findOne(id);
        String extension = "";
        int start = file.getOriginalFilename().lastIndexOf('.');
        if (start != -1)
            extension = file.getOriginalFilename().substring(start).trim();
        String fileName;
        Path targetLocation;
        Path comicBookDir = storageLocation.resolve(comicBookDTO.getId().toString());
        if (!Files.exists(comicBookDir)) {
            try {
                Files.createDirectories(comicBookDir);
            } catch (IOException e) {
                throw new FileSystemException("Could not create comic book directory: "
                    + comicBookDir.getFileName());
            }
        }
        switch (type) {
            case "background": {
                fileName = comicBookDTO.getId() + "-background" + extension;
                targetLocation = comicBookDir.resolve(fileName);
                comicBookDTO.setImagePath(targetLocation.toString());
                break;
            }
            case "cover": {
                fileName = comicBookDTO.getId() + "-cover" + extension;
                targetLocation = comicBookDir.resolve(fileName);
                comicBookDTO.setCoverPath(targetLocation.toString());
                break;
            }
            default:
                throw new BadRequestException("Unknown parameter: " + type);
        }
        try {
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
            byte[] bytes = file.getBytes();

            Files.write(targetLocation, bytes);
        } catch (IOException e) {
            throw new FileSystemException("Could not store file " + fileName);
        }
        return save(comicBookDTO);
    }
}

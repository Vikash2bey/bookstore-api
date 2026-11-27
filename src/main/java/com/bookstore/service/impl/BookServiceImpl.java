package com.bookstore.service.impl;

import com.bookstore.dto.BookDto;
import com.bookstore.mapper.BookMapper;
import com.bookstore.repository.BookRepository;
import com.bookstore.service.BookService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BookServiceImpl implements BookService {

    private final BookRepository bookRepository;
    private final BookMapper bookMapper;

    @Override
    @Transactional(readOnly = true)
    public List<BookDto> getAllBooks() {
        return bookRepository.findByActiveTrueOrderByTitleAsc().stream()
                .map(bookMapper::toDto)
                .toList();
    }
}

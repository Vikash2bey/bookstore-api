package com.bookstore.mapper;

import com.bookstore.dto.BookDto;
import com.bookstore.entity.Book;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface BookMapper {

    @Mapping(target = "active", source = "active")
    BookDto toDto(Book book);
}

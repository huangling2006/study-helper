package com.hl.study.wrong.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WrongBookMapper {
    @Insert("INSERT INTO wrong_book (user_id, question_id, wrong_count, last_wrong_time, add_time) " +
            "VALUES (#{userId}, #{questionId}, 1, NOW(), NOW()) " +
            "ON DUPLICATE KEY UPDATE wrong_count = wrong_count + 1, last_wrong_time = NOW()")
    int upsertWrongBook(@Param("userId") Long userId, @Param("questionId") Long questionId);
}
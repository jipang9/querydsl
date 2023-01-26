package study.querydsl.dto;

import com.querydsl.core.annotations.QueryProjection;
import lombok.Data;

@Data
public class MemberDto {

    private String username;
    private int age;

    public MemberDto() {
    }

    @QueryProjection // 붙이면 dto도 Q파일로 만들어 줌.
    public MemberDto(String username, int age) {
        this.username = username;
        this.age = age;
    }
}

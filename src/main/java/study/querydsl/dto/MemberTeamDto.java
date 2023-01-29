package study.querydsl.dto;


import com.querydsl.core.annotations.QueryProjection;
import lombok.Data;

@Data
public class MemberTeamDto {

  private Long memberId;
  private String username;
  private int age;
  private Long teamId;
  private String teamName;

  @QueryProjection  // 이 어노테이션을 사용함으로써 순수 dto가 아닌 querydsl에 의존성이 있는 dto가 되어버리는 단점 발생
  public MemberTeamDto(Long memberId, String username, int age, Long teamId,
      String teamName) {
    this.memberId = memberId;
    this.username = username;
    this.age = age;
    this.teamId = teamId;
    this.teamName = teamName;
  }
}

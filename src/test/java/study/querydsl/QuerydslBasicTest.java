package study.querydsl;


import static org.assertj.core.api.Assertions.assertThat;
import static study.querydsl.entity.QMember.member;
import static study.querydsl.entity.QTeam.team;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.QueryResults;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.ExpressionUtils;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceContext;
import javax.persistence.PersistenceUnit;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import study.querydsl.dto.MemberDto;
import study.querydsl.dto.QMemberDto;
import study.querydsl.dto.UserDto;
import study.querydsl.entity.Member;
import study.querydsl.entity.QMember;
import study.querydsl.entity.Team;

@Transactional
@SpringBootTest
public class QuerydslBasicTest {

  @PersistenceContext
  EntityManager em;

  JPAQueryFactory queryFactory;

  @BeforeEach
  public void before() {
    queryFactory = new JPAQueryFactory(em);
    Team teamA = new Team("teamA");
    Team teamB = new Team("teamB");
    em.persist(teamA);
    em.persist(teamB);

    Member member1 = new Member("member1", 10, teamA);
    Member member2 = new Member("member2", 20, teamA);
    Member member3 = new Member("member3", 30, teamB);
    Member member4 = new Member("member4", 40, teamB);

    em.persist(member1);
    em.persist(member2);
    em.persist(member3);
    em.persist(member4);
  }

  @Test
  public void startJPQL() {
    //member1을 찾아라
    String queryString = "select m from Member m where m.username = :username";
    Member findMember = em.createQuery(queryString, Member.class)
        .setParameter("username", "member1")
        .getSingleResult();

    assertThat(findMember.getUsername()).isEqualTo("member1");
  }

  @Test
  public void startQuerydsl() {
//        QMember m = new QMember("m"); // 2) 어떤 qmember인지 구분 ( 별칭 ) - 크게 중요하지 않다. == QMember.member;

    Member findMember = queryFactory
        .select(member)
        .from(member)
        .where(member.username.eq("member1")) // 파라미터 바인딩을 안해도 됌 -> 자동으로 해줌 == 파라미터 바인딩 처리
        .fetchOne();

    assertThat(findMember.getUsername()).isEqualTo("member1");

    // 2개의 차이 ?
    // 실행 시점에서 오타를 확인할 수 있음 (만약 일반 버전이면) -> 런타임 오류로 확인한다...
    // querydsl은 컴파일 시점에서 오류를 발견할 수 있음. 파라미터 바인딩을 자동으로 해준다 (2개의 가장 큰 장점)

  }

  @Test
  public void search() {
    Member findMember = queryFactory
        .selectFrom(member)
        .where(member.username.eq("member1")
            .and(member.age.eq(10)))
        .fetchOne();

    assertThat(findMember.getUsername()).isEqualTo("member1");
  }

  @Test
  public void searchAndParam() {
    Member findMember = queryFactory
        .selectFrom(member)
        .where(
            member.username.eq("member1"), // 동일하게 and로 조회됨.
            member.age.eq(10)
        )
        .fetchOne();
    assertThat(findMember.getUsername()).isEqualTo("member1");
  }

  @Test
  public void resultFetch() {
    // List
    List<Member> fetch = queryFactory
        .selectFrom(member)
        .fetch();

    // 단 건
    Member fetchOne = queryFactory
        .selectFrom(member)
        .fetchOne();
    // 처음 한 건 조회
    Member fetchFirst = queryFactory
        .selectFrom(member)
        .fetchFirst();

    // 페이징에서 사용
    QueryResults<Member> results = queryFactory
        .selectFrom(member)
        .fetchResults();
    results.getTotal();
    List<Member> content = results.getResults();
    // 복잡하고 성능이 중요한 페이징에선 fetchresults와 count를 사용하면 안된다 => query 2방 날리는게 더 나음음
  }

  /**
   * 회원 정렬 순서 1. 나이 내림차순 desc 2. 이름 올림차순 asc 3. 단, 2에서 회원 이름이 없으면 마지막에 출력 (nulls last)
   */
  @Test
  public void sort() {

    em.persist(new Member(null, 100));
    em.persist(new Member("member5", 100));
    em.persist(new Member("member6", 100));

    List<Member> result = queryFactory
        .selectFrom(member)
        .where(member.age.eq(100))
        .orderBy(member.age.desc(), member.username.asc().nullsLast())
        .fetch();

    Member member5 = result.get(0);
    Member member6 = result.get(1);
    Member memberNull = result.get(2);

    assertThat(member5.getUsername()).isEqualTo("member5");
    assertThat(member6.getUsername()).isEqualTo("member6");
    assertThat(memberNull.getUsername()).isNull();
  }

  @Test
  public void paging1() {
    List<Member> list = queryFactory.selectFrom(member)
        .orderBy(member.username.desc())
        .offset(1)
        .limit(2)
        .fetch();

    assertThat(list.size()).isEqualTo(2);
  }


  @Test
  public void paging2() {
    QueryResults<Member> queryResults = queryFactory.selectFrom(member)
        .orderBy(member.username.desc())
        .offset(1)
        .limit(2)
        .fetchResults();
//                .fetch();

    assertThat(queryResults.getTotal()).isEqualTo(4);
    assertThat(queryResults.getLimit()).isEqualTo(2);
    assertThat(queryResults.getOffset()).isEqualTo(1);
    assertThat(queryResults.getResults().size()).isEqualTo(2);

    // .fetchResult() 대신엔 -> . fetch()
    // .fetchCount() 대신엔 -> fetch().size()

  }

  @Test
  public void aggregation() {
    List<Tuple> list = queryFactory.select(
            member.count(),
            member.age.sum(),
            member.age.avg(),
            member.age.max(),
            member.age.min()
        ).from(member)
        .fetch();

    Tuple tuple = list.get(0);
    assertThat(tuple.get(member.count())).isEqualTo(4);
    assertThat(tuple.get(member.age.sum())).isEqualTo(100);
    assertThat(tuple.get(member.age.avg())).isEqualTo(25);
    assertThat(tuple.get(member.age.max())).isEqualTo(40);
    assertThat(tuple.get(member.age.min())).isEqualTo(10);
  }

  /**
   * 팀의 이름과 각 팀의 평균 연령을 구해라*
   */
  @Test
  public void group() {
    //given
    List<Tuple> result = queryFactory
        .select(team.name, member.age.avg())
        .from(member)
        .join(member.team, team)
        .groupBy(team.name)
        .fetch();

    Tuple teamA = result.get(0);
    Tuple teamB = result.get(1);
    assertThat(teamA.get(team.name)).isEqualTo("teamA");
    assertThat(teamA.get(member.age.avg())).isEqualTo(15);

    assertThat(teamB.get(team.name)).isEqualTo("teamB");
    assertThat(teamB.get(member.age.avg())).isEqualTo(35);

  }

  /**
   * 팀A에 소속된 모든 회원
   */
  @Test
  public void join() {
    List<Member> result = queryFactory
        .selectFrom(member)
        .join(member.team, team)
        .where(team.name.eq("teamA"))
        .fetch();
    assertThat(result).extracting("username").containsExactly("member1", "member2");
  }

  /**
   * 세타 조인 회원의 이름이 팀 이름과 같은 회원 조회* 한가지 제약  -> 세타 방식을 쓰면 외부 조인을 못쓴다*
   */
  @Test
  public void theta_join() {
    em.persist(new Member("teamA"));
    em.persist(new Member("teamB"));
    em.persist(new Member("teamC"));

    List<Member> result = queryFactory
        .select(member)
        .from(member, team)
        .where(member.username.eq(team.name))
        .fetch();

    assertThat(result)
        .extracting("username")
        .containsExactly("teamA", "teamB");
  }

  /**
   * * 예 ) 회원과 팀을 조인하면서, 팀 이름이 teamA인 팀만 조인해라, 회원은 모두 조회 JPQL => select m, t from Member m, left
   * join m.team t on t.name = 'teamA' *
   */
  @Test
  public void join_on_filtering() {
    List<Tuple> result = queryFactory.select(member, team)
        .from(member)
        .join(member.team, team)
        .on(team.name.eq("teamA")) // left 조인일 시 -> on
        .fetch();

    for (Tuple tuple : result) {
      System.out.println("tuple : " + tuple);
    }
  }

  /**
   * 연관관계가 없는 엔티티 외부 조인 회원의 이름이 팀 이름과 같은 대상 외부 조인  * *
   */
  @Test
  public void join_on_no_relation() {
    em.persist(new Member("teamA"));
    em.persist(new Member("teamB"));
    em.persist(new Member("teamC"));

    List<Tuple> result = queryFactory
        .select(member, team)
        .from(member)
        .leftJoin(team).on(member.username.eq(team.name))
        .fetch();

    for (Tuple tuple : result) {
      System.out.println("tuple : " + tuple);
    }
  }

  @PersistenceUnit
  EntityManagerFactory emf;

  @Test
  public void fetchJoinNo() {
    em.flush();
    em.clear();

    Member member1 = queryFactory
        .selectFrom(member)
        .where(member.username.eq("member1"))
        .fetchOne();

    boolean loaded = emf.getPersistenceUnitUtil().isLoaded(member1.getTeam());
    assertThat(loaded).as(" 패치 조인 미적용 ").isFalse();

  }

  @Test
  public void fetchJoinUse() {
    em.flush();
    em.clear();

    Member member1 = queryFactory
        .selectFrom(member)
        .join(member.team, team).fetchJoin()
        .where(member.username.eq("member1"))
        .fetchOne();

    boolean loaded = emf.getPersistenceUnitUtil().isLoaded(member1.getTeam());
    assertThat(loaded).as(" 패치 조인").isTrue();

  }

  /**
   * 나이가 가장 많은 회원 조회*
   */
  @Test
  public void subQuery() {
    QMember memberSub = new QMember("memberSub");
    List<Member> result = queryFactory
        .selectFrom(member)
        .where(member.age.eq(
            JPAExpressions
                .select(memberSub.age.max())
                .from(memberSub)
        ))
        .fetch();

    assertThat(result).extracting("age")
        .containsExactly(40);

  }

  /**
   * 나이가 평균 이상인 회원
   */
  @Test
  public void subQueryGoe() {
    QMember memberSub = new QMember("memberSub");
    List<Member> result = queryFactory
        .selectFrom(member)
        .where(member.age.goe(
            JPAExpressions
                .select(memberSub.age.avg())
                .from(memberSub)
        ))
        .fetch();

    assertThat(result).extracting("age")
        .containsExactly(30, 40);

  }

  @Test
  public void simpleProjection() {
    List<String> fetch = queryFactory.select(member.username)
        .from(member) // select가 여러개면 Tuple 형태로 들어온다는 점
        .fetch();
    for (String s : fetch) {
      System.out.println(" fetch : " + s);
    }
  }

  @Test
  public void tupleProjection() {
    List<Tuple> tuples = queryFactory.select(member.username, member.age)
        .from(member) // select가 여러개면 Tuple 형태로 들어온다는 점
        .fetch();

    for (Tuple tuple : tuples) {
      System.out.println("username : " + tuple.get(member.username));
      System.out.println("age : " + tuple.get(member.age));
    }

  }

  /**
   * 순수 JPA에서 DTO 조회 코드*
   */
  // new 명령어를 사용해야하고, dto의 패키지 이름을 다적어줘야해서 지저분하다는 단점이 있다. 또한 생성자 방식만 지원한다는 점!
  @Test
  public void fuelJpaReturnByDto() {

    List<MemberDto> resultList = em.createQuery(
            "select new study.querydsl.dto.MemberDto(m.username, m.age)" +
                "from Member  m", MemberDto.class)
        .getResultList();

    for (MemberDto memberDto : resultList) {
      System.out.println("resultList (username) is : " + memberDto.getUsername());
      System.out.println("resultList (age) is : " + memberDto.getAge());
    }
  }


  /**
   * queryDsl 빈 생성*
   */
  // 결과를 DTO 반환할 때 사용, ( 3가지 방식을 지원한다 1. 프로퍼티 접근, 2. 필드 직접 접근, 3. 생성자 사용)
  @Test
  public void QuerydslBeanCreate() {

    // 방식 1 ) - > Setter ( 프로퍼티 방법 ) => getter & setter가 없으면 안 됌
//        List<MemberDto> result = queryFactory.select(
//                        Projections.bean(MemberDto.class,
//                                member.username,
//                                member.age))
//                .from(member)
//                .fetch();

    // 방식 2 ) -> fields  => field에 그냥 바로 꽂아버림
//         List<MemberDto> result = queryFactory.select(
//                         Projections.fields(MemberDto.class,
//                                 member.username,
//                                 member.age))
//                 .from(member)
//                 .fetch();

    // 방식 3 ) -> 생성자 방식  => 타입이 무조건 맞아야 함.
    List<MemberDto> result = queryFactory
        .select(Projections.constructor(MemberDto.class,
            member.username,
            member.age))
        .from(member)
        .fetch();
    for (MemberDto memberDto : result) {
      System.out.println("data is : " + memberDto);
    }

  }

  @Test
  public void findUserDto() {
    QMember memberSub = new QMember("memberSub");
    List<UserDto> result = queryFactory
        .select(Projections.constructor(UserDto.class,
            member.username.as("name"),

            ExpressionUtils.as(JPAExpressions
                .select(memberSub.age.max())
                .from(memberSub), "age")
        ))
        .from(member)
        .fetch();

    for (UserDto userDto : result) {
      System.out.println("data is : " + userDto);
    }
  }

  /**
   * @QueryProjecion 활용*
   */
  @Test
  public void QuerydslBasicTest() {

    // 이 방식의 단점 1. Qfile의 생성 ( dto에 @QueryProjection 사용)
    //              2. dto가 querydsl에 의존성이 증가.
    List<MemberDto> data = queryFactory
        .select(new QMemberDto(member.username, member.age))
        .from(member)
        .fetch();

    for (MemberDto datum : data) {
      System.out.println("memberDTo is " + datum);
    }

  }


  /**
   * 동적 쿼리  - booleanBuilder 사용 * 동적 쿼리를 유연하게 and 조건을 이용해서 검색 가능하다. *
   */
  @Test
  public void booleanBuilder() {
    String nameParam = "member1";
    Integer ageParam = 10;

    List<Member> result = searchMember1(nameParam, ageParam);
    Assertions.assertThat(result.size()).isEqualTo(1);

  }

  private List<Member> searchMember1(String nameParam, Integer ageParam) {
    // 들어온 파라미터의 조건에 따라서 값이 바뀌어야한다 .
    BooleanBuilder builder = new BooleanBuilder();

    // and 조건
    if (nameParam != null) {
      builder.and(member.username.eq(nameParam));
    }
    if (ageParam != null) {
      builder.and(member.age.eq(ageParam));
    }

    return queryFactory
        .selectFrom(member)
        .where(builder)
        .fetch();
  }

  /**
   * 동적 쿼리  - Where 다중 파라미터 사용 *
   */
  @Test
  public void dynamicQuery_WhereParam() {

    String nameParam = "member1";
    Integer ageParam = 10;

    List<Member> result = searchMember2(nameParam, ageParam);
    Assertions.assertThat(result.size()).isEqualTo(1);
  }

  private List<Member> searchMember2(String nameParam, Integer ageParam) {
    return queryFactory
        .selectFrom(member)
//                .where(usernameEqual(nameParam), ageEqual(ageParam))
        .where(allEqual(nameParam, ageParam))
        .fetch();
  }

  private BooleanExpression usernameEqual(String nameParam) {
    return nameParam != null ? member.username.eq(nameParam) : null;
  }

  private BooleanExpression ageEqual(Integer ageParam) {
    return ageParam != null ? member.age.eq(ageParam) : null;
  }

  // 이렇게 메소드를 분리하면 아래와 같은 장점이 생김 ( 조립이 가능하다는 점. )  + ( 재사용성에서도 유리한 이점이 있다 ) + ( query의 가독성이 증가한다 )
  private BooleanExpression allEqual(String nameParam, Integer ageParam) {
    return usernameEqual(nameParam).and(ageEqual(ageParam));
  }

  @Test
  public void bulkUpdate() {

    long count = queryFactory.update(member)
        .set(member.username, "비회원")
        .where(member.age.lt(28))
        .execute();

    em.flush();
    em.clear();

    //왜 스프링에서는 영속성 컨택스트라는 개념이 존재할까? -> 해결
    List<Member> list = queryFactory.selectFrom(member).fetch();
    for (Member member1 : list) {
      System.out.println("member : " + member1);
    }
  }

  @Test
  public void bulkAdd() {
    long l = queryFactory.update(member).set(member.age, member.age.add(1)).execute();

    List<Member> list = queryFactory.selectFrom(member).fetch();
    for (Member member1 : list) {
      System.out.println("member : " + member1);
    }

    em.flush();
    em.clear();

    List<Member> list2 = queryFactory.selectFrom(member).fetch();
    for (Member member1 : list2) {
      System.out.println("member : " + member1);
    }

  }

  @Test
  public void sqlFunction() {

    List<String> result = queryFactory
        .select(Expressions.stringTemplate(
            "function('replace', {0}, {1}, {2})",
            member.username, "member", "M"))
        .from(member)
        .fetch();

    for (String s : result) {
      System.out.println("member : " + s);
    }

  }


}

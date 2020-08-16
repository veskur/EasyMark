package easymark.webserver.routes;

import easymark.*;
import easymark.database.*;
import easymark.database.models.*;
import easymark.webserver.*;
import easymark.webserver.constants.*;
import io.javalin.*;
import io.javalin.http.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.stream.*;

import static easymark.webserver.WebServerUtils.*;
import static io.javalin.core.security.SecurityUtil.roles;

public class IndexRoutes {
    public static void configure(Javalin app) {

        app.post("/login", ctx -> {
            final ForbiddenResponse FORBIDDEN = new ForbiddenResponse("Forbidden");

            if (!checkCSRFToken(ctx, ctx.formParam(FormKeys.CSRF_TOKEN)))
                throw FORBIDDEN;

            String providedAccessTokenStr = ctx.formParam("accessToken");
            logIn(ctx, providedAccessTokenStr);
            ctx.sessionAttribute(SessionKeys.LAST_SESSION_ACTION, LocalDateTime.now());
            ctx.redirect("/");
        });

        app.post("/logout", ctx -> {
            if (!checkCSRFToken(ctx, ctx.formParam(FormKeys.CSRF_TOKEN)))
                throw new ForbiddenResponse("Forbidden");
            logOut(ctx);
            ctx.redirect("/");
        });

        app.get("/", ctx -> {
            Map<String, Object> model = new HashMap<>();
            model.put(ModelKeys.CSRF_TOKEN, makeCSRFToken(ctx));

            Set<UserRole> roles = ctx.sessionAttribute(SessionKeys.ROLES);
            if (roles != null) {

                if (roles.contains(UserRole.ADMIN)) {
                    UUID adminId = ctx.sessionAttribute(SessionKeys.ENTITY_ID);
                    model.put(ModelKeys.ADMIN_ID, adminId);
                    String uek = null;
                    try {
                        uek = getUekFromContext(ctx);
                    } catch (Exception e) {
                    }
                    final String _uek = uek;

                    try (DatabaseHandle db = DBMS.openRead()) {
                        Set<UUID> courseIDs = new HashSet<>();
                        List<Course> courses = db.get()
                                .getCourses()
                                .stream()
                                .filter(course -> adminId.equals(course.getAdminId()))
                                .sorted(Comparator.comparingInt(Course::getOrdNum))
                                .peek(course -> {
                                    courseIDs.add(course.getId());
                                })
                                .collect(Collectors.toUnmodifiableList());
                        model.put(ModelKeys.COURSES, courses);

                        List<TestRequest> testRequests = new ArrayList<>();
                        model.put(ModelKeys.TEST_REQUESTS, testRequests);
                        Map<UUID, String> chapterNamePerChapter = new HashMap<>();
                        model.put(ModelKeys.CHAPTER_NAME_PER_CHAPTER, chapterNamePerChapter);
                        Map<UUID, String> courseNamePerChapter = new HashMap<>();
                        model.put(ModelKeys.COURSE_NAME_PER_CHAPTER, courseNamePerChapter);
                        for (Chapter ch : db.get().getChapters()) {
                            if (courseIDs.contains(ch.getCourseId())) {
                                List<TestRequest> newTestRequests = db.get().getTestRequests()
                                        .stream()
                                        .filter(tr -> tr.getChapterId().equals(ch.getId()))
                                        .collect(Collectors.toUnmodifiableList());
                                testRequests.addAll(newTestRequests);
                                if (newTestRequests.size() > 0) {
                                    chapterNamePerChapter.put(ch.getId(), ch.getName());
                                    courseNamePerChapter.computeIfAbsent(ch.getId(), chId -> db.get().getCourses()
                                            .stream()
                                            .filter(c -> c.getId().equals(ch.getCourseId()))
                                            .findAny()
                                            .orElseThrow()
                                            .getName());
                                }
                            }
                        }
                        testRequests.sort(Comparator.comparing(TestRequest::getTimestamp).reversed());

                        Map<UUID, String> participantNamePerParticipant = new HashMap<>();
                        model.put(ModelKeys.PARTICIPANT_NAME_PER_PARTICIPANT, participantNamePerParticipant);
                        testRequests.stream()
                                .map(TestRequest::getParticipantId)
                                .distinct()
                                .map(pId -> db.get().getParticipants()
                                        .stream()
                                        .filter(p -> p.getId().equals(pId))
                                        .findAny()
                                        .orElseThrow())
                                .forEach(p -> {
                                    try {
                                        participantNamePerParticipant.put(p.getId(), Cryptography.decryptData(p.getName(), p.getNameSalt(), _uek));
                                    } catch (Exception e) {
                                        participantNamePerParticipant.put(p.getId(), "Decryption failure");
                                    }
                                });
                    }
                    ctx.render("pages/index.admin.peb", model);
                    return;

                } else if (roles.contains(UserRole.PARTICIPANT)) {
                    UUID participantId = ctx.sessionAttribute(SessionKeys.ENTITY_ID);
                    Course course;
                    List<Chapter> chapters;
                    Map<UUID, List<Assignment>> assignmentsPerChapter;
                    Map<UUID, AssignmentResult> assignmentResultPerAssignment = new HashMap<>();
                    Map<UUID, Assignment> testAssignmentPerChapter = new HashMap<>();
                    Set<UUID> chaptersWithTestRequests = new HashSet<>();
                    AtomicInteger totalScore = new AtomicInteger();
                    AtomicInteger maxScore = new AtomicInteger();
                    try (DatabaseHandle db = DBMS.openRead()) {
                        UUID courseId = db.get().getParticipants()
                                .stream()
                                .filter(p -> p.getId().equals(participantId))
                                .findAny()
                                .orElseThrow(() -> new BadRequestResponse("You were not found in the database"))
                                .getCourseId();
                        course = db.get().getCourses()
                                .stream()
                                .filter(c -> c.getId().equals(courseId))
                                .findAny()
                                .orElseThrow(() -> new InternalServerErrorResponse("Course not found in database"));
                        assignmentsPerChapter = new HashMap<>();
                        chapters = db.get().getChapters()
                                .stream()
                                .filter(chapter -> chapter.getCourseId().equals(courseId))
                                .peek(chapter -> {
                                    AtomicReference<Assignment> testAssignment = new AtomicReference<>();

                                    List<Assignment> assignments = db.get().getAssignments()
                                            .stream()
                                            .filter(assignment -> assignment.getChapterId().equals(chapter.getId()))
                                            .filter(assignment -> {
                                                boolean isTestAssignment = assignment.getId().equals(chapter.getTestAssignmentId());
                                                if (isTestAssignment) {
                                                    testAssignmentPerChapter.put(chapter.getId(), assignment);
                                                    testAssignment.set(assignment);
                                                }
                                                return !isTestAssignment;
                                            })
                                            .sorted(Comparator.comparingInt(Assignment::getOrdNum))
                                            .collect(Collectors.toUnmodifiableList());
                                    assignmentsPerChapter.put(chapter.getId(), assignments);

                                    Stream.concat(
                                            assignments.stream(),
                                            Stream.ofNullable(testAssignment.get())
                                    ).forEach(a -> {
                                        db.get().getAssignmentResults()
                                                .stream()
                                                .filter(ar -> ar.getParticipantId().equals(participantId) && a.getId().equals(ar.getAssignmentId()))
                                                .findAny()
                                                .ifPresent(ar -> {
                                                    assignmentResultPerAssignment.put(a.getId(), ar);
                                                    totalScore.addAndGet(ar.getScore());
                                                    maxScore.addAndGet(a.getMaxScore());
                                                });
                                    });

                                    if (chapter.getTestAssignmentId() != null) {
                                        boolean hasTestRequest = db.get().getTestRequests()
                                                .stream()
                                                .anyMatch(tr -> tr.getChapterId().equals(chapter.getId()) &&
                                                        tr.getParticipantId().equals(participantId));
                                        if (hasTestRequest)
                                            chaptersWithTestRequests.add(chapter.getId());
                                    }
                                })
                                .sorted(Comparator.comparingInt(Chapter::getOrdNum))
                                .collect(Collectors.toUnmodifiableList());
                    }

                    Utils.GradingInfo gradingInfo = Utils.gradingInfo(totalScore.get(), maxScore.get());
                    model.put(ModelKeys.TOTAL_SCORE, gradingInfo.score);
                    model.put(ModelKeys.MAX_SCORE, gradingInfo.maxScore);
                    model.put(ModelKeys.RATIO, gradingInfo.ratioPercentStr);
                    model.put(ModelKeys.GRADE, gradingInfo.gradeStr);

                    model.put(ModelKeys.COURSE, course);
                    model.put(ModelKeys.CHAPTERS, chapters);
                    model.put(ModelKeys.ASSIGNMENTS_PER_CHAPTER, assignmentsPerChapter);
                    model.put(ModelKeys.ASSIGNMENT_RESULT_PER_ASSIGNMENT, assignmentResultPerAssignment);
                    model.put(ModelKeys.TEST_ASSIGNMENT_PER_CHAPTER, testAssignmentPerChapter);
                    model.put(ModelKeys.CHAPTERS_WITH_TEST_REQUESTS, chaptersWithTestRequests);
                    model.put(ModelKeys.CSRF_TOKEN, makeCSRFToken(ctx));
                    ctx.render("pages/index.participant.peb", model);
                    return;
                }
            }
            ctx.render("pages/index.peb", model);
        });


        app.get("/settings", ctx -> {
            List<Admin> admins;
            try (DatabaseHandle db = DBMS.openRead()) {
                admins = db.get().getAdmins();
            }

            UUID entityId = ctx.sessionAttribute(SessionKeys.ENTITY_ID);
            if (entityId == null)
                throw new InternalServerErrorResponse();

            Map<UUID, List<Course>> coursesPerAdmin;
            try (DatabaseHandle db = DBMS.openRead()) {
                coursesPerAdmin = db.get().getCourses()
                        .stream()
                        .collect(Collectors.groupingBy(Course::getAdminId));
            }

            ctx.render("pages/settings.peb", Map.of(
                    ModelKeys.ADMINS, admins,
                    ModelKeys.ENTITY_ID, entityId,
                    ModelKeys.COURSES_PER_ADMIN, coursesPerAdmin,
                    ModelKeys.CSRF_TOKEN, makeCSRFToken(ctx)
            ));
        }, roles(UserRole.ADMIN));
    }
}

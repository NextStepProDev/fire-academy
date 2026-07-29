package pl.fireacademy.api.trainingcalendar;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.fireacademy.api.trainingcalendar.TrainingCalendarDtos.AthleteSummary;
import pl.fireacademy.domain.user.User;
import pl.fireacademy.domain.user.UserRepository;

import java.util.List;

/** Coach-side roster of 1-on-1 clients — the entry point into a single person's calendar. */
@Service
public class AdminAthleteService {

    private static final String AVATAR_FOLDER = "avatars";

    private final UserRepository userRepository;

    public AdminAthleteService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<AthleteSummary> list() {
        return userRepository.findByAthleteTrueOrderByFirstNameAscLastNameAsc().stream()
                .map(AdminAthleteService::toSummary)
                .toList();
    }

    private static AthleteSummary toSummary(User user) {
        String avatarUrl = user.getAvatarFilename() != null
                ? "/api/files/" + AVATAR_FOLDER + "/" + user.getAvatarFilename()
                : null;
        return new AthleteSummary(user.getId(), user.getFirstName(), user.getLastName(),
                user.getEmail(), avatarUrl);
    }
}

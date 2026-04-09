package com.jobhunter.service;

import com.jobhunter.config.JwtUtil;
import com.jobhunter.dto.AuthResponse;
import com.jobhunter.dto.LoginRequest;
import com.jobhunter.dto.RegisterRequest;
import com.jobhunter.exception.EmailAlreadyRegisteredException;
import com.jobhunter.exception.InvalidPasswordException;
import com.jobhunter.exception.UserNotFoundException;
import com.jobhunter.model.User;
import com.jobhunter.repository.UserRepository;
import com.jobhunter.scheduler.SchedulerService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final SchedulerService schedulerService;

    public AuthResponse register(RegisterRequest request){
        if( userRepository.existsByEmail(request.getEmail()) ){
            throw new EmailAlreadyRegisteredException("Email already registered");
        }

        User user = new User();
        user.setEmail(request.getEmail());
        user.setPassword( passwordEncoder.encode(request.getPassword()) );
        user.setName(request.getName());
        userRepository.save(user);

        schedulerService.scheduleUserJob(user.getId());

        String jwtToken = jwtUtil.generateToken(user.getEmail());
        return new AuthResponse( jwtToken , user.getEmail() , user.getName() );
    }

    public AuthResponse login( LoginRequest request){

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        if( !passwordEncoder.matches( request.getPassword() , user.getPassword() )){
            throw new InvalidPasswordException("Invalid password");
        }

        String jwtToken = jwtUtil.generateToken(user.getEmail());
        return new AuthResponse( jwtToken , user.getEmail() , user.getName() );
    }

    public User getByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
    }

    public void linkTelegramAccount(String email, Long telegramChatId) {
        User user = getByEmail(email);
        user.setTelegramChatId(telegramChatId);
        userRepository.save(user);
    }

}

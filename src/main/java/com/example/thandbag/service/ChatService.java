package com.example.thandbag.service;


import com.example.thandbag.Enum.AlarmType;
import com.example.thandbag.dto.*;
import com.example.thandbag.model.Alarm;
import com.example.thandbag.model.ChatContent;
import com.example.thandbag.model.ChatRoom;
import com.example.thandbag.model.User;
import com.example.thandbag.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
@Service
public class ChatService {
    private final ChannelTopic channelTopic;
    private final RedisTemplate redisTemplate;
    private final ChatRedisRepository chatRedisRepository;
    private final UserRepository userRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatContentRepository chatContentRepository;
    private final AlarmRepository alarmRepository;


    public String getNickname(String username) {
        return userRepository.findByUsername(username).get().getNickname();
    }

    /**
     * destination정보에서 roomId 추출
     */
    public String getRoomId(String destination) {
        int lastIndex = destination.lastIndexOf('/');
        if (lastIndex != -1)
            return destination.substring(lastIndex + 1);
        else
            return "";
    }

    // 채팅방에 메시지 발송
    public void sendChatMessage(ChatMessageDto chatMessageDto) {
        String nickname = chatMessageDto.getSender();
        System.out.println("메시지 sender : " + chatMessageDto.getSender());
        System.out.println("메시지 메시지 : " + chatMessageDto.getMessage());
        chatMessageDto.setUserCount(chatRedisRepository.getUserCount(chatMessageDto.getRoomId()));
        if (ChatMessageDto.MessageType.ENTER.equals(chatMessageDto.getType())) {
            chatMessageDto.setMessage(chatMessageDto.getSender() + "님이 방에 입장했습니다.");
            chatMessageDto.setSender("[알림]");
        } else if (ChatMessageDto.MessageType.QUIT.equals(chatMessageDto.getType())) {
            chatMessageDto.setMessage(chatMessageDto.getSender() + "님이 방에서 나갔습니다.");
            chatMessageDto.setSender("[알림]");
        } else {
            Optional<User> user = userRepository.findByNickname(nickname);
            Optional<ChatRoom> chatRoom = chatRoomRepository.findById(chatMessageDto.getRoomId());
            Boolean readCheck = chatMessageDto.getUserCount() != 1;

            // 입장, 퇴장 알림을 제외한 메시지를 MYSQL에 저장
            ChatContent chatContent = ChatContent.builder()
                    .content(chatMessageDto.getMessage())
                    .user(user.get())
                    .chatRoom(chatRoom.get())
                    .isRead(readCheck)
                    .build();

            chatContentRepository.save(chatContent);
        }
        redisTemplate.convertAndSend(channelTopic.getTopic(), chatMessageDto);
    }

    // 채팅방 생성
    @Transactional
    public ChatRoomDto createChatRoom(CreateRoomRequestDto roomRequestDto) {
        // 서로 같은 사람이 다시 생성하려고 하면 안되게 함
        if (
                chatRoomRepository.existsAllByPubUserIdAndSubUserId(roomRequestDto.getPubId(), roomRequestDto.getSubId()) ||
                chatRoomRepository.existsAllByPubUserIdAndSubUserId(roomRequestDto.getSubId(), roomRequestDto.getPubId())
        ) {
            throw new IllegalArgumentException("이미 생성된 채팅방입니다.");
        }

        // Redis에 채팅방 저장
        ChatRoomDto chatRoomDto = chatRedisRepository.createChatRoom(roomRequestDto);
        ChatRoom chatRoom = new ChatRoom(
                chatRoomDto.getRoomId(),
                roomRequestDto.getPubId(),
                roomRequestDto.getSubId()
        );
        chatRoomRepository.save(chatRoom);

        // 알림 생성
        Alarm alarm = Alarm.builder()
                .userId(roomRequestDto.getSubId())
                .type(AlarmType.INVITEDCHAT)
                .pubId(chatRoom.getPubUserId())
                .alarmMessage(userRepository.getById(roomRequestDto.getPubId()).getNickname() + "님과의 새로운 채팅이 시작되었습니다.")
                .build();

        alarmRepository.save(alarm);

        // 알림 메시지를 보낼 DTO 생성
        AlarmResponseDto alarmResponseDto = AlarmResponseDto.builder()
                        .alarmId(alarm.getId())
                        .type(AlarmType.INVITEDCHAT.toString())
                        .message("[알림] 새로운 채팅방 생성 알림")
                        .chatRoomId(chatRoom.getId())
                        .alarmTargetId(chatRoom.getSubUserId())
                        .build();

        redisTemplate.convertAndSend(channelTopic.getTopic(), alarmResponseDto);

        return chatRoomDto;
    }

    // 내가 참가한 채팅방 목록
    public List<ChatMyRoomListResponseDto> findMyChatList(User user) {
        List<ChatRoom> chatRoomList = chatRoomRepository.findAllByPubUserIdOrSubUserId(user.getId(), user.getId());
        List<ChatMyRoomListResponseDto> responseDtoList = new ArrayList<>();
        String roomId;
        String subNickname;
        String subProfileImgUrl;
        String lastContent;
        String lastContentCreatedTime;
        int unreadCount;

        for (ChatRoom room : chatRoomList) {
            // 내가 pub 이면 sub아이디 찾아야 하고, sub이면 pub아이디 찾아야 함
            roomId = room.getId();
            User subUser = user.getId().equals(room.getSubUserId()) ? userRepository.getById(room.getPubUserId()) : userRepository.getById(room.getSubUserId());
            DateTimeFormatter newFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");
            subNickname = subUser.getNickname();
//            subProfileImgUrl = subUser.getProfileImg().getProfileImgUrl();
            subProfileImgUrl = "naver.com/asdfasdf.jpg";

            Optional<ChatContent> lastCont = chatContentRepository.findFirstByChatRoomOrderByCreatedAtDesc(room);
            if (lastCont.isPresent()) {
                lastContent = lastCont.get().getContent();
                lastContentCreatedTime = newFormatter.format(lastCont.get().getCreatedAt());
            } else {
                lastContent = "";
                lastContentCreatedTime = newFormatter.format(LocalDateTime.now());
            }

            // 읽지 않은 메시지 수
            unreadCount = chatContentRepository.findAllByUserNotAndChatRoomAndIsRead(user, room, false).size();

            ChatMyRoomListResponseDto dto = new ChatMyRoomListResponseDto(
                    roomId,
                    subNickname,
                    subProfileImgUrl,
                    lastContent,
                    lastContentCreatedTime,
                    unreadCount
            );
            responseDtoList.add(dto);
        }

        // 최신 메시지 시간을 기준으로 내림차순 정렬
        Collections.sort(responseDtoList);
        Collections.reverse(responseDtoList);

        return responseDtoList;
    }

    // 채팅방 입장 - 입장시 이전 대화 목록 불러오기
    @Transactional
    public List<ChatHistoryResponseDto> getTotalChatContents(String roomId) {
        ChatRoom room = chatRoomRepository.getById(roomId);
        System.out.println("roomId : " + roomId);
        System.out.println(room.getId());
        List<ChatContent> chatContentList = chatContentRepository.findAllByChatRoomOrderByCreatedAtAsc(room);
        System.out.println(chatContentList);
        List<ChatHistoryResponseDto> chatHistoryList = new ArrayList<>();
        DateTimeFormatter newFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");
        for (ChatContent chat : chatContentList) {
            String createdTime = newFormatter.format(chat.getCreatedAt());
            if (!chat.getIsRead()) {
                chat.setIsRead(true);
            }
            ChatHistoryResponseDto historyResponseDto = new ChatHistoryResponseDto(
                    chat.getUser().getNickname(),
                    "www.naver.com/dsjd.jpg",
                    chat.getContent(),
                    createdTime
            );
            chatHistoryList.add(historyResponseDto);
        }
        return chatHistoryList;
    }
}
import { defineStore } from 'pinia';
import { ref } from 'vue';

export const useAudioStore = defineStore('audio', () => {
    // --- 상태 (State) ---
    const currentBgm = ref<HTMLAudioElement | null>(null);
    const currentType = ref<'main' | 'game' | null>(null); // 현재 재생 중인 타입
    const isMuted = ref(false); // 음소거 여부
    const volume = ref(0.3);    // 기본 볼륨 (0.0 ~ 1.0)

    // --- 오디오 파일 경로 ---
    const tracks = {
        main: '/sounds/bgm_main.mp3', // 로비, 커뮤니티 등
        game: '/sounds/bgm_game.mp3',   // 인게임
    };

    // --- 액션 (Actions) ---

    // BGM 재생 요청 (핵심 로직)
    const playBgm = (type: 'main' | 'game') => {
        // 1. 이미 같은 타입의 음악이 재생 중이면 아무것도 안 함
        if (currentType.value === type && currentBgm.value) {
            if (!currentBgm.value.paused) {
                return;
            }
            // 2. 같은 타입인데 멈춰있다면(자동 재생 차단 등) 다시 재생 시도
            currentBgm.value.play().catch(() => {
                console.log('사용자 인터랙션 필요 (브라우저 정책)');
            });
            return;
        }

        // 3. 다른 타입이거나 재생 중인 오디오가 없다면 교체
        if (currentBgm.value) {
            fadeOutAndStop(currentBgm.value, () => startNewTrack(type));
        } else {
            startNewTrack(type);
        }
    };

    // 내부 함수: 새 트랙 시작
    const startNewTrack = (type: 'main' | 'game') => {
        const audio = new Audio(tracks[type]);
        audio.loop = true; // 무한 반복
        audio.volume = isMuted.value ? 0 : volume.value;

        audio.play().catch(() => {
            console.log('사용자 인터랙션 필요 (브라우저 정책)');
        });

        currentBgm.value = audio;
        currentType.value = type;
    };

    // 페이드 아웃 효과 (부드럽게 전환)
    const fadeOutAndStop = (audio: HTMLAudioElement, callback: () => void) => {
        const fadeInterval = setInterval(() => {
            if (audio.volume > 0.05) {
                audio.volume -= 0.05; // 볼륨 줄이기
            } else {
                clearInterval(fadeInterval);
                audio.pause();
                audio.currentTime = 0;
                callback(); // 다음 곡 재생
            }
        }, 50); // 0.05초마다 실행
    };

    // 음소거 토글
    const toggleMute = () => {
        isMuted.value = !isMuted.value;
        if (currentBgm.value) {
            currentBgm.value.volume = isMuted.value ? 0 : volume.value;
        }
    };

    return { playBgm, toggleMute, isMuted };
});
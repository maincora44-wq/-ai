# 맞춰봄 · Reconcile Lite
서버 업로드 없이 사용자의 브라우저에서 두 파일을 거래번호별로 대사하는 무료 실험 서비스입니다. 공통 거래번호가 존재하는 두 파일에만 적용 가능합니다. 은행 입금 일괄 총액 대사나 수수료 해석 기능은 아직 없습니다.

## 사용법
1. A(기준)와 B(비교) 자료의 CSV 또는 XLSX 파일을 선택합니다. 첫 줄은 열 이름입니다.
2. 두 파일 각각 거래번호/금액 열을 지정합니다. 거래번호는 양쪽에서 동일한 기준이어야 합니다.
3. '차이 분석하기'를 눌러 일치·차이·누락을 확인하고 결과 CSV를 다운로드합니다.
4. 가입 없이 샘플 데이터로도 체험할 수 있습니다.

## 개인정보
CSV/XLSX 파일은 브라우저에서만 읽고 분석하며 서버로 전송하거나 저장하지 않습니다. 외부 코드 번들/폰트는 내려받을 수 있지만 파일 내용은 전송하지 않습니다. 정산·입금의 의미가 다르다면 비교 결과는 잘못 해석될 수 있습니다. 공식 회계 감사의 유일한 근거로 사용하지 마세요.

## 개발·테스트·배포
```bash
npm install
npm test
npm run build
```
Render Static Site: repo root에서 `cd reconcile-lite && npm install --no-audit --no-fund && npm test && npm run build`, publish directory `reconcile-lite/dist`. 배포는 사용자 승인 아래 무료 정적 호스팅으로 하며 외부 API나 DB가 없습니다.

## 수요 검증
- 방문 건수와 실제 대사 완료 건수를 혼동하지 않습니다.
- 브라우저에서 파일 처리한 성공 이벤트와 샘플 처리 이벤트를 구분합니다. 추적방지(DNT)가 켜진 브라우저에서는 이벤트를 보내지 않습니다.
- `/signals/`의 1픽셀 대체 SVG GET 요청으로 액션별 총 횟수만 측정합니다. 파일 내용, 거래번호, 금액, 쿠키, 사용자 ID는 전송하지 않습니다. 호스팅 서비스의 표준 접속 로그는 별개입니다.
- Render Metrics → HTTP request count(HTTP Path)로 `/signals/analysis-real-*.svg` 총 횟수, `/signals/analysis-demo.svg` 체험, `/signals/download.svg` 다운로드, `/signals/helpful.svg`, `/signals/notfit.svg` 의견 클릭을 확인합니다.
- 이는 고유 사용자 수가 아니라 **이벤트 횟수**이며 같은 사용자의 반복 실행과 자기 테스트가 포함됩니다. 정적 CDN 집계 특성상 실제 수보다 적거나 집계 지연이 있을 수 있습니다.
- 공개 GitHub Issue로 자세한 의견도 받을 수 있습니다. 개인정보·실제 파일 업로드 금지.

## 한계
첫 시트 사용(XLSX), CSV UTF-8 또는 CP949, 최대 8MB/20,000행, 거래번호 단위 합계. 수수료, 환율, 정산 주기, 주문 분할 정산 등은 자동 해석하지 않습니다.

/* global __ENV, __ITER, __VU */
// noinspection NpmUsedModulesInstalled
import http from 'k6/http';
// noinspection NpmUsedModulesInstalled
import { check, sleep } from 'k6';
// noinspection NpmUsedModulesInstalled
import { Counter, Trend } from 'k6/metrics';

const baseUrl = __ENV.BASE_URL || 'http://localhost:8081';
const chatBase = Number(__ENV.CHAT_BASE || '900000000000');
const chatCount = Number(__ENV.CHAT_COUNT || '1000');
const vus = Number(__ENV.VUS || '16');
const writeRatio = Number(__ENV.WRITE_RATIO || '0.01');
const rampUpDuration = __ENV.RAMP_UP_DURATION || '1m';
const stageDuration = __ENV.STAGE_DURATION || '5m';
const rampDownDuration = __ENV.RAMP_DOWN_DURATION || '30s';

const getDuration = new Trend('tracked_links_get_duration', true);
const getRequests = new Counter('tracked_links_get_requests');
const getStatus200 = new Counter('tracked_links_get_status_200');
const getStatus500 = new Counter('tracked_links_get_status_500');
const getStatus502504 = new Counter('tracked_links_get_status_502_504');
const getOtherErrors = new Counter('tracked_links_get_other_errors');

const postDuration = new Trend('tracked_links_post_duration', true);
const postRequests = new Counter('tracked_links_post_requests');
const postStatus200 = new Counter('tracked_links_post_status_200');
const postStatus500 = new Counter('tracked_links_post_status_500');
const postStatus502504 = new Counter('tracked_links_post_status_502_504');
const postOtherErrors = new Counter('tracked_links_post_other_errors');

const deleteDuration = new Trend('tracked_links_delete_duration', true);
const deleteRequests = new Counter('tracked_links_delete_requests');
const deleteStatus200 = new Counter('tracked_links_delete_status_200');
const deleteStatus500 = new Counter('tracked_links_delete_status_500');
const deleteStatus502504 = new Counter('tracked_links_delete_status_502_504');
const deleteOtherErrors = new Counter('tracked_links_delete_other_errors');

// noinspection JSUnusedGlobalSymbols
export const options = {
  scenarios: {
    tracked_links_cache: {
      executor: 'ramping-vus',
      stages: [
        { duration: rampUpDuration, target: vus },
        { duration: stageDuration, target: vus },
        { duration: rampDownDuration, target: 0 },
      ],
    },
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  thresholds: {
    http_req_failed: ['rate<0.05'],
  },
};

// noinspection JSUnusedGlobalSymbols
export default function () {
  const chatId = chatBase + randomInt(1, chatCount);
  if (Math.random() < writeRatio) {
    mutateTrackedLink(chatId);
  } else {
    listTrackedLinks(chatId);
  }

  sleep(Number(__ENV.SLEEP_SECONDS || '0.05'));
}

function listTrackedLinks(chatId) {
  const response = http.get(`${baseUrl}/links`, {
    headers: {
      'Tg-Chat-Id': String(chatId),
    },
    tags: {
      endpoint: 'GET /links',
    },
  });

  check(response, {
    'GET /links returns 200': (r) => r.status === 200,
    'GET /links returns list body': (r) => r.json('size') !== undefined,
  });
  recordHttpStats(response, getDuration, getRequests, getStatus200, getStatus500, getStatus502504, getOtherErrors);
}

function mutateTrackedLink(chatId) {
  const link = `https://github.com/load-test/mutation-${__VU}-${__ITER}-${Date.now()}`;
  const headers = {
    'Content-Type': 'application/json',
    'Tg-Chat-Id': String(chatId),
  };
  const addBody = JSON.stringify({
    link,
    tags: ['load-test'],
    filters: ['author:load-test'],
  });

  const addResponse = http.post(`${baseUrl}/links`, addBody, {
    headers,
    tags: {
      endpoint: 'POST /links',
    },
  });

  check(addResponse, {
    'POST /links returns 200': (r) => r.status === 200,
  });
  recordHttpStats(
    addResponse,
    postDuration,
    postRequests,
    postStatus200,
    postStatus500,
    postStatus502504,
    postOtherErrors,
  );

  const removeBody = JSON.stringify({ link });
  const removeResponse = http.del(`${baseUrl}/links`, removeBody, {
    headers,
    tags: {
      endpoint: 'DELETE /links',
    },
  });

  check(removeResponse, {
    'DELETE /links returns 200': (r) => r.status === 200,
  });
  recordHttpStats(
    removeResponse,
    deleteDuration,
    deleteRequests,
    deleteStatus200,
    deleteStatus500,
    deleteStatus502504,
    deleteOtherErrors,
  );
}

function recordHttpStats(response, duration, requests, status200, status500, status502504, otherErrors) {
  requests.add(1);
  duration.add(response.timings.duration);

  if (response.status === 200) {
    status200.add(1);
  } else if (response.status === 500) {
    status500.add(1);
  } else if (response.status === 502 || response.status === 504) {
    status502504.add(1);
  } else {
    otherErrors.add(1);
  }
}

function randomInt(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

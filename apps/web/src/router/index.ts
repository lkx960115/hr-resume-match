import { createRouter, createWebHistory } from 'vue-router'
import AppLayout from '../layouts/AppLayout.vue'
import HomePage from '../pages/HomePage.vue'
import JobPage from '../pages/JobPage.vue'
import CandidatePage from '../pages/CandidatePage.vue'
import InterviewDetailPage from '../pages/InterviewDetailPage.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      component: AppLayout,
      children: [
        { path: '', name: 'home', component: HomePage },
        { path: 'jobs/:jobId', name: 'job', component: JobPage },
        { path: 'candidates/:candidateId', name: 'candidate', component: CandidatePage },
        {
          path: 'candidates/:candidateId/interview',
          name: 'interview-detail',
          component: InterviewDetailPage,
        },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

export default router

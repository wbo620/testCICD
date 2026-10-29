<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { fetchGrid, fetchValue, fetchVariables } from './api'
import type { GridResponse, ValueResponse, VariableInfo } from './types'

const variables = ref<VariableInfo[]>([])
const grid = ref<GridResponse | null>(null)
const result = ref<ValueResponse | null>(null)
const loading = ref(false)
const error = ref('')

const form = ref({
  variable: 'Visibility_surface',
  lat: 39.9,
  lon: 116.4,
  time: '',
})

/** 可选的要素变量（排除坐标轴与辅助变量）。 */
const selectableVariables = computed(() =>
  variables.value.filter((v) => v.dimensions.length === 3),
)

async function loadVariables() {
  try {
    variables.value = await fetchVariables()
  } catch (e) {
    error.value = (e as Error).message
  }
}

async function loadGrid() {
  if (!form.value.variable) return
  try {
    grid.value = await fetchGrid(form.value.variable)
  } catch (e) {
    error.value = (e as Error).message
  }
}

async function submit() {
  loading.value = true
  error.value = ''
  try {
    result.value = await fetchValue({
      variable: form.value.variable,
      lat: Number(form.value.lat),
      lon: Number(form.value.lon),
      time: form.value.time || undefined,
    })
  } catch (e) {
    error.value = (e as Error).message
    result.value = null
  } finally {
    loading.value = false
  }
}

function useLatLon(lat: number, lon: number) {
  form.value.lat = lat
  form.value.lon = lon
}

onMounted(async () => {
  await loadVariables()
  await loadGrid()
})
</script>

<template>
  <div class="page">
    <header class="page-header">
      <h1>GRIB2 要素查询</h1>
      <p>按经纬度与时次读取 GFS GRIB2 文件中的气象要素值</p>
    </header>

    <section class="card">
      <h2>查询条件</h2>
      <form class="grid-form" @submit.prevent="submit">
        <div class="field">
          <label for="variable">要素变量</label>
          <select id="variable" v-model="form.variable" @change="loadGrid">
            <option v-for="v in selectableVariables" :key="v.name" :value="v.name">
              {{ v.name }}
            </option>
          </select>
        </div>
        <div class="field">
          <label for="lat">纬度 (°N)</label>
          <input id="lat" v-model.number="form.lat" type="number" step="0.25" required />
        </div>
        <div class="field">
          <label for="lon">经度 (°E)</label>
          <input id="lon" v-model.number="form.lon" type="number" step="0.25" required />
        </div>
        <div class="field">
          <label for="time">时次 (可留空)</label>
          <input id="time" v-model="form.time" type="text" placeholder="2026-07-01T00:00:00Z" />
        </div>
        <div class="actions">
          <button type="submit" :disabled="loading">{{ loading ? '查询中…' : '查询' }}</button>
          <button type="button" class="ghost" @click="loadGrid">刷新网格信息</button>
        </div>
      </form>

      <p v-if="error" class="alert">{{ error }}</p>
    </section>

    <section v-if="result" class="card">
      <h2>
        查询结果
        <span class="badge" :class="result.exact ? 'ok' : 'warn'">
          {{ result.exact ? '精确命中' : '最近邻匹配' }}
        </span>
      </h2>
      <dl class="result-grid">
        <div>
          <dt>要素值</dt>
          <dd>{{ result.value.toFixed(3) }} {{ result.units ?? '' }}</dd>
        </div>
        <div>
          <dt>命中纬度</dt>
          <dd>{{ result.matchedLat }} °N</dd>
        </div>
        <div>
          <dt>命中经度</dt>
          <dd>{{ result.matchedLon }} °E</dd>
        </div>
        <div>
          <dt>命中时次</dt>
          <dd>{{ result.matchedTime ?? '-' }}</dd>
        </div>
        <div>
          <dt>格点下标</dt>
          <dd>{{ result.latIndex }} / {{ result.lonIndex }}</dd>
        </div>
      </dl>
    </section>

    <section v-if="grid" class="card">
      <h2>网格信息 — {{ grid.variable }}</h2>
      <dl class="result-grid">
        <div>
          <dt>格点规模</dt>
          <dd>{{ grid.latCount }} × {{ grid.lonCount }}</dd>
        </div>
        <div>
          <dt>纬度范围</dt>
          <dd>{{ grid.latMin }} ~ {{ grid.latMax }}</dd>
        </div>
        <div>
          <dt>经度范围</dt>
          <dd>{{ grid.lonMin }} ~ {{ grid.lonMax }}</dd>
        </div>
        <div>
          <dt>时次数</dt>
          <dd>{{ grid.times.length }}</dd>
        </div>
      </dl>
      <div class="actions">
        <button type="button" class="ghost" @click="useLatLon(grid.latMax, grid.lonMin)">
          取北极点
        </button>
        <button type="button" class="ghost" @click="useLatLon(0, grid.lonMin)">取赤道</button>
      </div>
    </section>

    <section class="card">
      <h2>文件中的变量</h2>
      <table>
        <thead>
          <tr>
            <th>名称</th>
            <th>维度</th>
            <th>单位</th>
            <th>格点数</th>
            <th>描述</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="v in variables" :key="v.name">
            <td>{{ v.name }}</td>
            <td class="muted">{{ v.dimensions.join(' × ') || '-' }}</td>
            <td class="muted">{{ v.units ?? '-' }}</td>
            <td class="muted">{{ v.latCount }} × {{ v.lonCount }}</td>
            <td class="muted">{{ v.description ?? '-' }}</td>
          </tr>
          <tr v-if="variables.length === 0">
            <td colspan="5" class="muted">暂无数据</td>
          </tr>
        </tbody>
      </table>
    </section>
  </div>
</template>

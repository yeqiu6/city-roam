Vue.component("footBar", {
  template: `
    <div class="foot">
    <button type="button" class="foot-box" :class="{active: activeBtn === 1}" @click="toPage(1)" aria-label="首页">
      <div class="foot-view"><span class="travel-icon travel-icon--home"></span></div>
      <div class="foot-text">首页</div>
    </button>
    <button type="button" class="foot-box" :class="{active: activeBtn === 2}" @click="toPage(2)" aria-label="漫游地图">
      <div class="foot-view"><span class="travel-icon travel-icon--map"></span></div>
      <div class="foot-text">漫游地图</div>
    </button>
    <button type="button" class="foot-box foot-box--publish" @click="toPage(0)" aria-label="发布笔记">
      <div class="foot-view"><span class="travel-icon travel-icon--publish"></span></div>
      <div class="foot-text">发布</div>
    </button>
    <button type="button" class="foot-box" :class="{active: activeBtn === 3}" @click="toPage(3)" aria-label="消息中心">
      <div class="foot-view"><span class="travel-icon travel-icon--message"></span></div>
      <div class="foot-text">消息</div>
    </button>
    <button type="button" class="foot-box" :class="{active: activeBtn === 4}" @click="toPage(4)" aria-label="我的">
      <div class="foot-view"><span class="travel-icon travel-icon--profile"></span></div>
      <div class="foot-text">我的</div>
    </button>
  </div>
  `,
  data() {
    return {
    }
  },
  props: ['activeBtn'],
  methods: {
    toPage(i) {
      if (i === 0) {
        location.href = "/blog-edit.html"
      } else if (i === 4) {
        location.href = "/info.html"
      } else if (i === 1){
        location.href = "/"
      } else if (i === 2) {
        CityRoamUI.comingSoon('漫游地图');
      } else if (i === 3) {
        CityRoamUI.comingSoon('消息中心');
      }
    }
  }
})

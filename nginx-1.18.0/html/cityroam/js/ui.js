(function (window, document) {
  function notify(message, type) {
    if (window.ELEMENT && window.ELEMENT.Message) {
      window.ELEMENT.Message({ message: message, type: type || 'info', duration: 1800 });
      return;
    }
    window.alert(message);
  }

  function release(vm, key) {
    vm.$set ? vm.$set(vm, key, false) : vm[key] = false;
  }

  window.CityRoamUI = {
    comingSoon: function (feature) {
      notify((feature || '该功能') + '即将上线，敬请期待', 'info');
    },
    lockAction: function (vm, key, action) {
      if (vm[key]) return Promise.resolve();
      vm.$set ? vm.$set(vm, key, true) : vm[key] = true;
      return Promise.resolve().then(action).then(function (result) {
        release(vm, key);
        return result;
      }, function (error) {
        release(vm, key);
        throw error;
      });
    }
  };

  document.addEventListener('error', function (event) {
    var image = event.target;
    if (!image || image.tagName !== 'IMG' || image.dataset.travelFallback) return;
    image.dataset.travelFallback = 'true';
    image.removeAttribute('src');
    image.classList.add('image-fallback');
    image.alt = image.alt || '图片加载失败';
  }, true);
})(window, document);

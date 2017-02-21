(ns commiteth.core
  (:require [reagent.core :as r]
            [re-frame.core :as rf]
            [secretary.core :as secretary]
            [goog.events :as events]
            [goog.history.EventType :as HistoryEventType]
            [markdown.core :refer [md->html]]
            [ajax.core :refer [GET POST]]
            [commiteth.ajax :refer [load-interceptors!]]
            [commiteth.handlers]
            [commiteth.subscriptions]
            [commiteth.activity :refer [activity-page]]
            [commiteth.repos :refer [repos-page]]
            [commiteth.bounties :refer [bounties-page]]
            [commiteth.update-address :refer [update-address-page]]
            [commiteth.manage :refer [manage-page]]
            [commiteth.issues :refer [issues-page]]
            [commiteth.common :refer [input]]
            [commiteth.config :as config]
            [commiteth.svg :as svg]
            [clojure.set :refer [rename-keys]]
            [re-frisk.core :refer [enable-re-frisk!]])
  (:import goog.History))


(defn flash-message-pane []
  (let [flash-message (rf/subscribe [:flash-message])]
    (fn []
      (when-let [[type message] @flash-message]
          [:div.flash-message {:class (name type)}
           [:i.close.icon {:on-click #(rf/dispatch [:clear-flash-message])}]
           [:p message]]))
    ))

(def user-dropdown-open? (r/atom false))

(defn user-dropdown [user items]
  (let [menu (if @user-dropdown-open?
                  [:div.ui.menu.transition.visible]
                  [:div.ui.menu])
        avatar-url (:avatar_url user)]
    [:div.ui.left.item.dropdown
     {:on-click #(swap! user-dropdown-open? not)}
     [:div.item
      [:img.ui.mini.circular.image {:src avatar-url}]]
     [:div.item
      (:login user)]
     [:div.item
      [:span.dropdown.icon]]
     (into menu
           (for [[target caption props] items]
             ^{:key target} [:div.item
                             [:a
                              (merge props
                                     (if (keyword? target)
                                       {:on-click #(rf/dispatch [target])}
                                       {:href target}))
                              caption]]))]))


(defn user-component [user]
  (let [user (rf/subscribe [:user])]
    (fn []
      (if @user
        [:div.ui.text.menu.user-component
         [:div.item
          [user-dropdown @user [[:update-address "Update address" {}]
                                ["/logout" "Sign out" {:class "logout-link"}]]]]]
        [:a.ui.button.small {:href js/authorizeUrl} "Sign in"]))))

(defn tabs []
  (let [user (rf/subscribe [:user])
        current-page (rf/subscribe [:page])]
    (fn []
      (let [tabs (apply conj [[:activity "Activity"]]
                        (when @user
                          [[:repos "Repositories"]
                           [:bounties "Bounties"]]))]
        (into [:div.ui.attached.tabular.menu.tiny.commiteth-tabs]
              (for [[page caption] tabs]
                (let [props {:class (str "ui item"
                                         (when (= @current-page page) " active"))
                             :on-click #(rf/dispatch [:set-active-page page])}]
                  ^{:key page} [:div props caption])))))))


(defn page-header []
  (let [user (rf/subscribe [:user])
        flash-message (rf/subscribe [:flash-message])]
    (fn []
      [:div.vertical.segment.commiteth-header
       [:div.ui.grid.container
        [:div.twelve.wide.column
         [:div.ui.image
          [:img.left.aligned {:src "/img/logo.svg"}]]]
        [:div.four.wide.column
         (if @flash-message
           [flash-message-pane]
           [user-component @user])]
        (when-not @user
          [:div.ui.text.content
           [:div.ui.divider.hidden]
           [:h2.ui.header "Commit ETH"]
           [:h2.ui.subheader "Earn ETH by committing to open source projects"]
           [:div.ui.divider.hidden]])
        [tabs]]])))

(def pages
  {:activity #'activity-page
   :repos #'repos-page
   :bounties #'bounties-page
   :update-address #'update-address-page})



(defn top-hunters []
  (let [top-hunters (rf/subscribe [:top-hunters])]
    (fn []
      (into [:div.ui.items.top-hunters]
            (map-indexed (fn [idx hunter]
                           [:div.item
                            [:div.leader-ordinal (str (+ 1 idx))]
                            [:div.ui..mini.circular.image
                             [:img {:src (:profile-image-url hunter)}]]
                            [:div.content
                             [:div.header (:display-name hunter)]
                             [:div.description (str "ETH " (:eth-earned hunter))]]])
                         @top-hunters)))))

(defn page []
  (fn []
    [:div.ui.pusher
     [page-header]
     [:div.ui.vertical.segment
      [:div.ui.container
       [:div.ui.grid.stackable
        [:div.ten.wide.computer.sixteen.wide.tablet.column
         [:div.ui.container.page-content
          [(pages @(rf/subscribe [:page]))]]]
        [:div.six.wide.column.computer.only
         [:div.ui.container.page-content
          [:h3 "Top hunters"]
          [top-hunters]]]]
       [:div.ui.divider]]]]))

(secretary/set-config! :prefix "#")

(secretary/defroute "/" []
  (rf/dispatch [:set-active-page :activity]))

(secretary/defroute "/manage" []
  (if js/user
    (rf/dispatch [:set-active-page :repos])
    (secretary/dispatch! "/")))

(defn hook-browser-navigation! []
  (doto (History.)
    (events/listen
     HistoryEventType/NAVIGATE
     (fn [event]
       (secretary/dispatch! (.-token event))))
    (.setEnabled true)))

(defn mount-components []
  (r/render [#'page] (.getElementById js/document "app")))

(defonce active-user (r/atom nil))

(defn load-user []
  (if-let [login js/user]
    (when-not (= login @active-user)
      (println "active user changed, loading user data")
      (reset! active-user login)
      (rf/dispatch [:set-active-user
                    {:login login
                     :id (js/parseInt js/userId)
                     :token js/token}]))
    (reset! active-user nil)))

(defn load-issues []
  (rf/dispatch [:load-bounties]))

(defn load-data []
  (load-issues)
  (load-user))

(defonce timer-id (r/atom nil))

(defn on-js-load []
  (when-not (nil? @timer-id)
    (js/clearInterval @timer-id))
  (reset! timer-id (js/setInterval load-data 60000))
  (mount-components))

(defn init! []
  (rf/dispatch-sync [:initialize-db])
  (when config/debug?
    (enable-re-frisk!))
  (load-interceptors!)
  (hook-browser-navigation!)
  (load-data)
  (on-js-load))

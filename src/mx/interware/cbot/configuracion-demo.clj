;; La siguiente estructura es un atom que tiene un mapa con las
;; diferentes configuraciones de cbot's la llave es el nombre del cbot
;; y el valor es un mapa que tiene :states :parameters :instances
;; :states es la llave a otro mapa con las definiciones capturada;
;; de StateInfo, :parameters es la llave de un mapa con las
;; definiciones de KeyVal generales para esta aplicacion y :instances
;; es la llave de un mapa que tiene como llaves las diferentes
;; instancias asociadas a sus parametros particulares. ej:

(def ejemplo
  {:Monitor-web {:interstate-delay "5000",
               :parameters {:subject "Muchos saludos"},
               :instances {:unica {:param-map {}}},
                 :states {:paso1 {:conf-map {:opr "send-mail-opr",
                                             :timeout "1000"
                                             :retry-count "2",
                                             :retry-delay "5000",
                                             :conf {:host "smtp.gmail.com",
                                                    :port "465",
                                                    :ssl true,
                                                    :user "robot@interware.com.mx",
                                                    :passwd "123456",
                                                    :to-vec ["fgerard@interware.com.mx"
                                                             "agarcia@interware.com.mx"
                                                             :administrador],
                                                    :subject :subject,
                                                    :text-vec ["Te mando este mail"
                                                               "Saludos"
                                                               :paso2
                                                               "bye"]}}},
                          :paso2 {:conf-map {:opr "socket-opr",
                                             :timeout "2000"
                                             :retry-count "0",
                                             :retry-delay "0",
                                             :conf {:host "10.1.1.66", :port "22"}}}}},
 :Monitor-site {:interstate-delay :delay,
                :parameters {
                             :host "10.1.1.53"},
                :instances {:primaria {:param-map {}},
                            :drp {:param-map {:host "10.1.1.99",
                                              :port "9080"}}},
                :states {:uno {
                               :conf-map {:opr "sleep-opr",
                                          :conf {:delta :delay}}
                               :flow {:x 123
                                      :y 44
                                      :connect [:dos ".*Exception.*" :tres]}},
                         :dos {
                               :conf-map {:opr "socket-opr",
                                          :conf {:host :host, :port :port}}
                               :flow {:x 215
                                      :y 88
                                      :connect [:uno]}}}}})


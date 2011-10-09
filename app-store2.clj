[:version 16]
[:updated 1317915580176]
[:configuration {:WEB {:interstate-delay "1000", :parameters {:port "22"}, :states [{:flow {:y 167, :x 4, :connect [:ssh?]}, :key :start, :conf-map {:opr "sleep-opr", :conf {:delta "5000"}}} {:flow {:y 156, :x 135, :connect [:MAL ".*Exception.*" :bien]}, :key :ssh?, :conf-map {:retry-count "3", :timeout "1000", :opr "socket-opr", :retry-delay "3000", :conf {:host :host, :port :port}}} {:flow {:y 225, :x 143, :connect [:context]}, :key :bien, :conf-map {:opr "print-msg-opr", :conf {:msg "SSH OK !!!"}}} {:flow {:y 226, :x 273, :connect [:context]}, :key :MAL, :conf-map {:opr "log-opr", :conf {:text "Promlenas :ssh?", :level :error}}} {:flow {:y 262, :x 33, :connect [:start]}, :key :context, :conf-map {:opr "print-context-opr", :conf {:filter ".*"}}}], :instances {:primaria {:param-map {:host "localhost", :port "22"}}}}}]

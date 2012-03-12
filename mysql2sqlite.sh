#!/bin/bash
cat $1 | \
awk '
# adapted from https://gist.github.com/943776
BEGIN {
	FS=",$"
	print "PRAGMA synchronous = OFF;"
	print "PRAGMA journal_mode = MEMORY;"
	print "BEGIN TRANSACTION;"
}

# Skip comments
/^\/\*/ { next }

# Print all `INSERT` lines. The single quotes are protected by another single quote.
/^INSERT/ {
	gsub( /\\\047/, "\047\047" )
	gsub(/\\n/, "\n")
	gsub(/\\r/, "\r")
	gsub(/\\"/, "\"")
	gsub(/\\\\/, "\\")
	gsub(/\\\032/, "\032")
	if ( match( $0, /^INSERT INTO \`[^\`]+\` VALUES / ) ) {
    stem = substr( $0, RSTART, RLENGTH )
    split( substr( $0, RSTART+RLENGTH+1 ), vals, /\),\(/)
    for(i=1;i<length(vals);i++) {
      print stem "(" vals[i] ");"
    }
  }
}

# Print the `CREATE` line as is and capture the table name.
/^CREATE/ {
	print
	if ( match( $0, /\`[^\`]+/ ) ) tableName = substr( $0, RSTART+1, RLENGTH-1 ) 
}

# Replace `FULLTEXT KEY` or any other `XXXXX KEY` except PRIMARY by `KEY`
/^  [^"]+KEY/ && !/^  PRIMARY KEY/ { gsub( /.+KEY/, "  KEY" ) }

# Get rid of field lengths in KEY lines
/ KEY/ { gsub(/\([0-9]+\)/, "") }

# Print all fields definition lines except the `KEY` lines.
/^  / && !/^(  KEY|\);)/ {
	gsub( /AUTO_INCREMENT|auto_increment/, "" )
	gsub( /(CHARACTER SET|character set) [^ ]+ /, "" )
	gsub( /DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP|default current_timestamp on update current_timestamp/, "" )
	gsub( /(COLLATE|collate) [^ ]+ /, "" )
	gsub(/(ENUM|enum)[^)]+\)/, "text ")
	gsub(/(SET|set)\([^)]+\)/, "text ")
	gsub(/UNSIGNED|unsigned/, "")
	if (prev) print prev ","
	prev = $1
}

# `KEY` lines are extracted from the `CREATE` block and stored in array for later print 
# in a separate `CREATE KEY` command. The index name is prefixed by the table name to 
# avoid a sqlite error for duplicate index name.
/^(  KEY|\))/ {
	if (prev) print prev
	prev=""
	if ( match( $0, /^\)/) ) {
		print ");"
	} else {
		if ( match( $0, /\"[^"]+/ ) ) indexName = substr( $0, RSTART+1, RLENGTH-1 ) 
		if ( match( $0, /\`[^`]+/ ) ) indexName = substr( $0, RSTART+1, RLENGTH-1 ) 
		if ( match( $0, /\([^()]+/ ) ) indexKey = substr( $0, RSTART+1, RLENGTH-1 ) 
		key[tableName]=key[tableName] "CREATE INDEX \"" tableName "_" indexName "\" ON \"" tableName "\" (" indexKey ");\n"
	}
}

# Print all `KEY` creation lines.
END {
	for (table in key) printf key[table]
	print "END TRANSACTION;"
}
'